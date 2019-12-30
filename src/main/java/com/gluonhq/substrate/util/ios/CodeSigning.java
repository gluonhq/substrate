/*
 * Copyright (c) 2019, Gluon
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
package com.gluonhq.substrate.util.ios;

import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.XcodeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.gluonhq.substrate.util.ios.Identity.IDENTITY_NAME_PATTERN;
import static com.gluonhq.substrate.util.ios.Identity.IDENTITY_ERROR_FLAG;
import static com.gluonhq.substrate.util.ios.Identity.IDENTITY_PATTERN;
import static com.gluonhq.substrate.util.ios.MobileProvision.MOBILE_PROVISION_EXTENSION;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class CodeSigning {

    // https://developer.apple.com/library/archive/technotes/tn2318/_index.html
    private static final String CODESIGN_OK_1 = "satisfies its Designated Requirement";
    private static final String CODESIGN_OK_2 = "valid on disk";
    private static final String CODESING_OK_3 = "explicit requirement satisfied";

    private static final String CODESIGN_ALLOCATE_ENV = "CODESIGN_ALLOCATE";
    private static final String EMBEDDED_PROVISIONING_PROFILE = "embedded.mobileprovision";
    private static String ERRLINK = "Please check https://docs.gluonhq.com/client/ for more information.";

    private static MobileProvision mobileProvision = null;
    private static Identity identity = null;
    private static List<MobileProvision> mobileProvisions;
    private static List<Identity> identities;

    // TODO
    private static String providedIdentityName; // if provided, use this one
    private static String providedMobileProvision;// if provided, use this one

    private String bundleId;
    private final ProcessPaths paths;
    private final InternalProjectConfiguration projectConfiguration;
    private final String sourceOS;

    private final Path appPath;
    private final Path tmpPath;

    public CodeSigning(ProcessPaths paths, InternalProjectConfiguration projectConfiguration) {
        this.paths = paths;
        this.projectConfiguration = projectConfiguration;
        this.sourceOS = projectConfiguration.getTargetTriplet().getOs().toString();
        this.bundleId = InfoPlist.getBundleId(InfoPlist.getPlistPath(paths, sourceOS), sourceOS);

        appPath = paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app");
        tmpPath = paths.getTmpPath();
    }

    public boolean signApp() throws IOException, InterruptedException {
        assertValidIdentity();
        MobileProvision mobileProvision = getProvisioningProfile();
        if (mobileProvision == null) {
            throw new RuntimeException("Provisioning profile not found.\n" +ERRLINK);
        }
        Path provisioningProfilePath = mobileProvision.getProvisioningPath();
        Path embeddedPath = appPath.resolve(EMBEDDED_PROVISIONING_PROFILE);
        Files.copy(provisioningProfilePath, embeddedPath, REPLACE_EXISTING);
        Path entitlementsPath = getEntitlementsPath(bundleId, true);
        Logger.logDebug("Signing with entitlements path: " + entitlementsPath);
        return sign(entitlementsPath, appPath);
    }

    private void assertValidIdentity() {
        List<Identity> identities = getIdentity();
        if ((identities == null) || identities.isEmpty()) {
            throw new RuntimeException("No valid Identity (Certificate) found for iOS development.\n"+ERRLINK);
        }
     }
    private MobileProvision getProvisioningProfile() throws IOException {
        if (bundleId == null) {
            bundleId = InfoPlist.getBundleId(InfoPlist.getPlistPath(paths, sourceOS), sourceOS);
        }

        if (mobileProvision == null) {
            List<Identity> identities = getIdentity();
            for (Identity identity : identities) {
                mobileProvision = findMobileProvision(identity, bundleId, bundleId);
                if (mobileProvision != null) {
                    if (providedMobileProvision == null
                            || providedMobileProvision.equals(mobileProvision.getName())) {
                        CodeSigning.identity = identity;
                        Logger.logDebug("Got provisioning profile: " + mobileProvision.getName());
                        return mobileProvision;
                    }
                }
            }
            Logger.logInfo("Warning, getProvisioningProfile is failing");
        }
        return mobileProvision;
    }

    private MobileProvision findMobileProvision(Identity identity, String bundleId, String initialBundleId) {
        Logger.logDebug("Mobile provision asked with bundleId = " + bundleId + " (initial bundleId: " + initialBundleId + ")");
        return retrieveValidMobileProvisions().stream()
                .filter(provision -> filterByIdentifier(provision, bundleId))
                .filter(provision -> filterByCertificate(provision, identity))
                .findFirst()
                .orElseGet(() -> tryModifiedBundleId(identity, bundleId, initialBundleId));
    }

    private boolean filterByIdentifier(MobileProvision provision, String bundleId) {
        Logger.logDebug("Checking mobile provision " + provision.getAppIdName());
        return provision.getAppIdentifier().equals(provision.getAppIdentifierPrefix() + "." + bundleId);
    }

    private Boolean filterByCertificate(MobileProvision provision, Identity identity) {
        return provision.getDeveloperCertificates().stream()
                .filter(certificate -> certificate.equals(identity.getSha1()))
                .findFirst()
                .map(c -> {
                    Logger.logDebug(provision.getName() + " matches " + identity);
                    return true;
                })
                .orElseGet(() -> {
                    Logger.logDebug("App identifiers match, but there are not fingerprint matches");
                    return false;
                });
    }

    private MobileProvision tryModifiedBundleId(Identity identity, String bundleId, String initialBundleId) {
        if (!bundleId.equals("*")) {
            if (bundleId.contains(".")) {
                String[] tokens = bundleId.split("\\.");
                int length = tokens.length - 1;
                int newLength = tokens[length].equals("*") ? length - 1 : length;
                String newBundleId = Stream.of(tokens)
                        .limit(newLength)
                        .collect(Collectors.joining("."));
                newBundleId = newBundleId.isEmpty() ? "*" : newBundleId.concat(".*");
                return findMobileProvision(identity, newBundleId, initialBundleId);
            } else {
                return findMobileProvision(identity, "*", initialBundleId);
            }
        }

        Logger.logInfo("No mobile provision was found matching signing identity '" + identity.getCommonName() +
                "' and app bundle ID '" + initialBundleId + "'");
        return null;
    }

    private static List<MobileProvision> retrieveValidMobileProvisions() {
        final LocalDate now = LocalDate.now();
        if (mobileProvisions == null) {
            mobileProvisions = retrieveAllMobileProvisions();
        }
        return mobileProvisions.stream()
                .filter(provision -> {
                    LocalDate expirationDate = provision.getExpirationDate();
                    return expirationDate != null && !expirationDate.isBefore(now);
                })
                .collect(Collectors.toList());
    }

    public static List<MobileProvision> retrieveAllMobileProvisions() {
        Path provisionPath = Paths.get(System.getProperty("user.home"), "Library", "MobileDevice", "Provisioning Profiles");
        if (!Files.exists(provisionPath) || !Files.isDirectory(provisionPath)) {
            Logger.logSevere("Invalid provisioning profiles folder at " + provisionPath.toString());
            return Collections.emptyList();
        }

        try {
            return Files.walk(provisionPath)
                    .filter(f -> f.toFile().getName().endsWith(MOBILE_PROVISION_EXTENSION))
                    .map(MobileProvision::new)
                    .sorted(Comparator.comparing(provision -> provision.getName().toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            Logger.logSevere("Invalid provisioning profiles files: " + ex.getMessage());
        }
        return Collections.emptyList();
    }

    private boolean sign(Path entitlementsPath, Path appPath) throws IOException, InterruptedException {
        if (identity == null) {
            getProvisioningProfile();
        }
        Identity identity = CodeSigning.identity;
        if (identity == null) {
            throw new RuntimeException("Error signing app: signing identity was null");
        }
        Logger.logDebug("Signing app with identity: " + identity);
        ProcessRunner runner = new ProcessRunner("codesign", "--force", "--sign", identity.getSha1());
        if (entitlementsPath != null) {
            runner.addArgs("--entitlements", entitlementsPath.toString());
        }
        if (projectConfiguration.isVerbose()) {
            runner.addArg("--verbose");
        }
        runner.addArg(appPath.toString());
        String codesignAllocate = XcodeUtils.getCommandForSdk("codesign_allocate", "iphoneos");
        runner.addToEnv(CODESIGN_ALLOCATE_ENV, codesignAllocate);
        if (!runner.runTimedProcess("codesign", 30)) {
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

    private NSDictionaryEx dictionary;

    private Path getEntitlementsPath(String bundleId, boolean taskAllow) throws IOException {
        getProvisioningProfile();
        Path entitlementsPath = tmpPath.resolve("Entitlements.plist");

        try (InputStream is = FileOps.resourceAsStream("/native/ios/Entitlements.plist")) {
            dictionary = new NSDictionaryEx(is);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IOException("Error reading default entitlements: ", ex);
        }

        if (mobileProvision != null) {
            NSDictionaryEx entitlements = mobileProvision.getEntitlements();
            Arrays.stream(entitlements.getAllKeys())
                    .filter(key -> dictionary.get(key) == null)
                    .forEach(key -> dictionary.put(key, entitlements.get(key)));

            dictionary.put("application-identifier", mobileProvision.getAppIdentifierPrefix() + "." + bundleId);
        }
        dictionary.put("get-task-allow", taskAllow);
        Logger.logDebug("Entitlements.plist = " + dictionary.getEntrySet());
        dictionary.saveAsXML(entitlementsPath);
        return entitlementsPath;
    }

    private static List<Identity> getIdentity() {
        if (providedIdentityName != null) {
            return findIdentityByName(providedIdentityName);
        }
        return findIdentityByPattern();
    }

    private static List<Identity> findIdentityByName(String name) {
        if (name == null) {
            return Collections.emptyList();
        }
        if (identities == null) {
            identities = retrieveAllIdentities();
        }
        return identities.stream()
                .filter(identity -> identity.getCommonName().equals(name))
                .collect(Collectors.toList());
    }

    public static List<Identity> findIdentityByPattern() {
        if (identities == null) {
            identities = retrieveAllIdentities();
        }
        return identities.stream()
                .filter(identity -> IDENTITY_NAME_PATTERN.matcher(identity.getCommonName()).find())
                .collect(Collectors.toList());
    }

    private static List<Identity> retrieveAllIdentities() {
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
