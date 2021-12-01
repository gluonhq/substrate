/*
 * Copyright (c) 2019, 2021, Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL GLUON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.substrate.util.macos;

import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.gluonhq.substrate.util.macos.Identity.IDENTITY_NAME_PATTERN;
import static com.gluonhq.substrate.util.macos.Identity.IDENTITY_PATTERN;
import static com.gluonhq.substrate.util.macos.Identity.IDENTITY_ERROR_FLAG;

public class CodeSigning {

    // https://developer.apple.com/library/archive/technotes/tn2318/_index.html
    private static final String CODESIGN_OK_1 = "satisfies its Designated Requirement";
    private static final String CODESIGN_OK_2 = "valid on disk";
    private static final String CODESING_OK_3 = "explicit requirement satisfied";

    private static final String ERRLINK = "Please check https://docs.gluonhq.com/ for more information.";

    private static final String KEYCHAIN_ERROR_MESSAGE = "errSecInternalComponent";

    private Identity identity = null;
    private List<Identity> identities;

    private final String providedIdentityName; // if provided, use this one
    private final InternalProjectConfiguration projectConfiguration;
    private final Path appPath;
    private final Path rootPath;

    public CodeSigning(ProcessPaths paths, InternalProjectConfiguration projectConfiguration) {
        this.projectConfiguration = projectConfiguration;
        appPath = paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app");
        rootPath = paths.getSourcePath().resolve(projectConfiguration.getTargetTriplet().getOs());
        providedIdentityName = projectConfiguration.getReleaseConfiguration().getProvidedSigningIdentity();
    }

    public boolean signApp() throws IOException, InterruptedException {
        assertValidIdentity();
        Path entitlementsPath = getEntitlementsPath();
        Logger.logDebug("Signing with entitlements path: " + entitlementsPath);
        return sign(entitlementsPath, appPath);
    }

    private void assertValidIdentity() {
        identities = getIdentity();
        if (identities == null || identities.isEmpty()) {
            throw new RuntimeException("No valid Identity (Certificate) found for macOS development.\n" + ERRLINK);
        }
        Logger.logDebug("There are " + identities.size() + " possible valid identities");
    }

    private boolean sign(Path entitlementsPath, Path appPath) throws IOException, InterruptedException {
        if (identity == null) {
            identity = identities.stream()
                    .findFirst()
                    .orElseThrow(() -> new IOException("Error signing app: signing identity was null"));
        }
        Logger.logDebug("Signing app with identity: " + identity);
        ProcessRunner execRunner = new ProcessRunner("codesign", "--options", "runtime", "--force", "--sign", identity.getSha1());
        if (entitlementsPath != null) {
            execRunner.addArgs("--entitlements", entitlementsPath.toString());
        }
        if (projectConfiguration.isVerbose()) {
            execRunner.addArg("--verbose");
        }
        execRunner.addArg(appPath.resolve("Contents").resolve("MacOS").resolve(projectConfiguration.getAppName()).toString());
        if (!execRunner.runTimedProcess("codesign executable", 30)) {
            Logger.logSevere("Codesign process failed");
            return false;
        }

        for (String line : execRunner.getResponses()) {
            if (line.contains(KEYCHAIN_ERROR_MESSAGE)) {
                Logger.logInfo("Error signing the application: the keychain was locked.\nYou will be required now to unlock the keychain");
                if (unlockKeychain()) {
                    return sign(entitlementsPath, appPath);
                }
                return false;
            }
        }

        ProcessRunner appRunner = new ProcessRunner("codesign", "--options", "runtime", "--force", "--sign", identity.getSha1());
        if (projectConfiguration.isVerbose()) {
            appRunner.addArg("--verbose");
        }
        appRunner.addArg(appPath.toString());
        if (!appRunner.runTimedProcess("codesign app", 30)) {
            Logger.logSevere("Codesign process failed");
            return false;
        }

        if (!verifyCodesign(appPath)) {
            Logger.logSevere("Codesign validation failed");
            return false;
        }

        Logger.logDebug("Signing done successfully");
        return true;
    }

    /**
     * When running on MacOS, if the Keychain is locked, a system dialog
     * will show up, and the user can unlock the keychain.
     *
     * However running from a remote session, this won't be the case, and
     * this method will try to unlock the user's keychain, but requires the user
     * intervention to type the password
     *
     * @return true if keychain was unlocked, false otherwise
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean unlockKeychain() throws IOException, InterruptedException {
        String keychain = ProcessRunner.runProcessForSingleOutput("keychain", "security", "default-keychain", "-d", "user");
        if (keychain == null || keychain.isEmpty()) {
            Logger.logSevere("User's Keychain not found. Can't unlock");
            return false;
        }
        keychain = keychain.trim().replaceAll("\"", "");
        if (!Files.exists(Path.of(keychain.trim()))) {
            Logger.logSevere("Invalid User's Keychain at " + keychain);
            return false;
        }

        // Note: this requires user's intervention
        String unlock = ProcessRunner.runProcessForSingleOutput("keychain unlock", "security", "unlock-keychain", keychain);
        if (unlock == null || !unlock.isEmpty()) {
            Logger.logSevere("Wrong keychain password. Can't unlock");
            return false;
        }
        Logger.logDebug("Keychain unlocked successfully");
        return true;
    }

    private boolean verifyCodesign(Path target) throws IOException, InterruptedException {
        Logger.logDebug("Validating codesign...");
        ProcessRunner runner = new ProcessRunner("codesign", "--verify", "-vvvv", target.toAbsolutePath().toString());
        if (runner.runTimedProcess("verify", 5)) {
            return runner.getResponses().stream()
                    .anyMatch(line -> line.contains(CODESIGN_OK_1) ||
                            line.contains(CODESIGN_OK_2) || line.contains(CODESING_OK_3));
        }
        return false;
    }

    private Path getEntitlementsPath() throws IOException {
        Path entitlements = rootPath.resolve("Entitlements.plist");
        if (Files.exists(entitlements)) {
            return entitlements;
        }
        return null;
    }

    private List<Identity> getIdentity() {
        if (providedIdentityName != null) {
            return findIdentityByName(providedIdentityName);
        }
        return findIdentityByPattern();
    }

    private List<Identity> findIdentityByName(String name) {
        if (name == null) {
            return Collections.emptyList();
        }
        if (identities == null) {
            identities = retrieveAllIdentities();
        }
        Logger.logDebug("Find identity by name from " + identities.size() + " identities");
        return identities.stream()
                .filter(identity -> identity.getCommonName().equals(name))
                .collect(Collectors.toList());
    }

    private List<Identity> findIdentityByPattern() {
        if (identities == null) {
            identities = retrieveAllIdentities();
        }
        Logger.logDebug("Find identity by pattern from " + identities.size() + " identities");
        return identities.stream()
                .filter(identity -> IDENTITY_NAME_PATTERN.matcher(identity.getCommonName()).find())
                .collect(Collectors.toList());
    }

    public static List<Identity> retrieveAllIdentities() {
        ProcessRunner runner = new ProcessRunner("security", "find-identity", "-p", "codesigning", "-v");
        try {
            if (runner.runProcess("security") == 0) {
                return runner.getResponses().stream()
                        .map(line -> IDENTITY_PATTERN.matcher(line.trim()))
                        .filter(Matcher::find)
                        .map(matcher -> {
                            String flags = matcher.group(3);
                            if (flags == null || !flags.startsWith(IDENTITY_ERROR_FLAG)) {
                                return new Identity(matcher.group(1), matcher.group(2));
                            }
                            return null;
                        })
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(identity -> identity.getCommonName().toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        } catch (IOException | InterruptedException e) {
            Logger.logFatal(e, "There was an error retrieving identities for codesigning: " + e.getMessage());
        }
        return new ArrayList<>();
    }

}
