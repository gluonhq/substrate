/*
 * Copyright (c) 2021, Gluon
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
package com.gluonhq.substrate.util.windows;

import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.ReleaseConfiguration;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.gluonhq.substrate.Constants.DEFAULT_APP_VERSION;

public class MSIBundler {

    private final ProcessPaths paths;
    private final InternalProjectConfiguration projectConfiguration;
    private final String sourceOS;
    private final Path rootPath;

    public MSIBundler(ProcessPaths paths, InternalProjectConfiguration projectConfiguration) {
        this.paths = paths;
        this.projectConfiguration = projectConfiguration;
        this.sourceOS = projectConfiguration.getTargetTriplet().getOs();
        this.rootPath = paths.getSourcePath().resolve(sourceOS);
    }

    public boolean createPackage(boolean sign) throws IOException, InterruptedException {
        final String appName = projectConfiguration.getAppName();
        final String version = Optional.ofNullable(projectConfiguration.getReleaseConfiguration().getVersionName()).orElse(DEFAULT_APP_VERSION);
        Path localAppPath = paths.getAppPath().resolve(appName + ".exe");
        if (!Files.exists(localAppPath)) {
            throw new IOException("Error: " + appName + ".exe not found");
        }
        Logger.logInfo("Building exe for " + localAppPath);

        /**
         * Create directories and copy resources
         */
        Path tmpMSI = paths.getTmpPath().resolve("tmpMSI");
        if (Files.exists(tmpMSI)) {
            FileOps.deleteDirectory(tmpMSI);
        }
        Files.createDirectories(tmpMSI);

        Path userAssets = rootPath.resolve(Constants.WIN_ASSETS_FOLDER);
        // copy assets to gensrc/windows
        Path windowsGenSrcPath = paths.getGenPath().resolve(sourceOS);
        Path windowsAssetPath = windowsGenSrcPath.resolve(Constants.WIN_ASSETS_FOLDER);
        Files.createDirectories(windowsAssetPath);
        // Copy system resources and over-write it with user provided resources
        FileOps.copyDirectoryFromResources("/native/windows/assets", windowsAssetPath);
        if (Files.exists(userAssets)) {
            FileOps.copyDirectory(userAssets, windowsAssetPath);
        }

        Path config = tmpMSI.resolve("config");
        Files.createDirectories(config);
        Path wixPath = config.resolve("main.wxs");
        Path wixObjPath = wixPath.getParent().resolve(wixPath.getFileName() + ".wixobj");
        Path msiPath = paths.getGvmPath().getParent().resolve(appName + "-" +  version  + ".msi");
        FileOps.copyResource("/native/windows/wix/main.wxs", wixPath);
        FileOps.copyDirectory(windowsAssetPath, config);

        /**
         * Wix Compile
         */
        Map<String, String> userInput = createAppDetailMap();
        List<String> processArgs = new ArrayList<>(List.of(
                WixTool.CANDLE.getPath(),
                "-nologo",
                wixPath.toString(),
                "-ext", "WixUtilExtension",
                "-arch", "x64",
                "-out", wixObjPath.toString()
        ));

        userInput.entrySet().stream()
                .map(wixVar -> String.format("-d%s=%s", wixVar.getKey(), wixVar.getValue()))
                .forEachOrdered(processArgs::add);
        Logger.logInfo(String.join(" ", processArgs));
        ProcessRunner candle = new ProcessRunner(processArgs.toArray(new String[0]));
        if (candle.runProcess("Wix Compiler") != 0) {
            throw new IOException("Error running candle to generate wixobj");
        }

        /**
         * Wix Link
         */
        processArgs.clear();
        processArgs.addAll(List.of(
                WixTool.LIGHT.getPath(),
                "-nologo",
                "-spdb",
                "-sw1076", // https://github.com/wixtoolset/issues/issues/5938
                "-ext", "WixUtilExtension",
                "-ext", "WixUIExtension",
                "-out", msiPath.toString(),
                wixObjPath.toString()
        ));

        ProcessRunner light = new ProcessRunner(processArgs.toArray(new String[0]));
        if (light.runProcess("Wix Linker") != 0) {
            throw new IOException("Error running light to generate msi");
        }

        return true;
    }

    private Map<String, String> createAppDetailMap() {

        Map<String, String> userInput = new HashMap<>();
        ReleaseConfiguration releaseConfiguration = projectConfiguration.getReleaseConfiguration();

        String appName = projectConfiguration.getAppName();
        String executableName = appName + ".exe";
        String vendor = Optional.ofNullable(releaseConfiguration.getVendor()).orElse("Unknown");
        String version = Optional.ofNullable(releaseConfiguration.getVersionName()).orElse(DEFAULT_APP_VERSION);
        userInput.put("GSProductCode", createUUID("ProductCode", appName, vendor, version).toString());
        userInput.put("GSAppName", appName);
        userInput.put("GSAppExecutable", executableName);
        userInput.put("GSAppVersion", version);
        userInput.put("GSAppVendor", vendor);
        userInput.put("GSAppIconName", appName + ".ico");
        userInput.put("GSAppIcon", paths.getTmpPath().resolve("tmpMSI").resolve("config").resolve("icon.ico").toString());
        userInput.put("GSMainExecutableGUID", createUUID("GSMainExecutableGUID", appName, vendor, version).toString());
        userInput.put("GSStartMenuShortcutGUID", createUUID("StartMenuShortcutGUID", appName, vendor, version).toString());
        userInput.put("GSDesktopShortcutGUID", createUUID("DesktopShortcutGUID", appName, vendor, version).toString());
        Path license = paths.getTmpPath().resolve("tmpMSI").resolve("config").resolve("license.rtf");
        if (Files.exists(license)) {
            ensureByMutationFileIsRTF(license);
            userInput.put("GSLicenseRtf", license.toString());
        }
        userInput.put("GSApplicationPath", paths.getClientPath().resolve("x86_64-windows").resolve(executableName).toString());
        userInput.put("GSProductUpgradeCode", createUUID("UpgradeCode", appName, vendor, version).toString());
        userInput.put("GSAppDescription", Optional.ofNullable(releaseConfiguration.getAppDescription()).orElse("some-app-description"));
        return userInput;
    }

    private UUID createUUID(String prefix,String appName, String vendor, String version) {
        String key = String.join(",", prefix, appName, vendor, version);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static void ensureByMutationFileIsRTF(Path f) {
        if (f == null || !Files.isRegularFile(f)) return;

        try {
            boolean existingLicenseIsRTF = false;

            try (InputStream fin = Files.newInputStream(f)) {
                byte[] firstBits = new byte[7];

                if (fin.read(firstBits) == firstBits.length) {
                    String header = new String(firstBits);
                    existingLicenseIsRTF = "{\\rtf1\\".equals(header);
                }
            }

            if (!existingLicenseIsRTF) {
                List<String> oldLicense = Files.readAllLines(f);
                try (Writer w = Files.newBufferedWriter(
                        f, Charset.forName("Windows-1252"))) {
                    w.write("{\\rtf1\\ansi\\ansicpg1252\\deff0\\deflang1033"
                            + "{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}\n"
                            + "\\viewkind4\\uc1\\pard\\sa200\\sl276"
                            + "\\slmult1\\lang9\\fs20 ");
                    oldLicense.forEach(l -> {
                        try {
                            for (char c : l.toCharArray()) {
                                // 0x00 <= ch < 0x20 Escaped (\'hh)
                                // 0x20 <= ch < 0x80 Raw(non - escaped) char
                                // 0x80 <= ch <= 0xFF Escaped(\ 'hh)
                                // 0x5C, 0x7B, 0x7D (special RTF characters
                                // \,{,})Escaped(\'hh)
                                // ch > 0xff Escaped (\\ud###?)
                                if (c < 0x10) {
                                    w.write("\\'0");
                                    w.write(Integer.toHexString(c));
                                } else if (c > 0xff) {
                                    w.write("\\ud");
                                    w.write(Integer.toString(c));
                                    // \\uc1 is in the header and in effect
                                    // so we trail with a replacement char if
                                    // the font lacks that character - '?'
                                    w.write("?");
                                } else if ((c < 0x20) || (c >= 0x80) ||
                                        (c == 0x5C) || (c == 0x7B) ||
                                        (c == 0x7D)) {
                                    w.write("\\'");
                                    w.write(Integer.toHexString(c));
                                } else {
                                    w.write(c);
                                }
                            }
                            // blank lines are interpreted as paragraph breaks
                            if (l.length() < 1) {
                                w.write("\\par");
                            } else {
                                w.write(" ");
                            }
                            w.write("\r\n");
                        } catch (IOException e) {
                            Logger.logDebug(e.getMessage());
                        }
                    });
                    w.write("}\r\n");
                }
            }
        } catch (IOException e) {
            Logger.logDebug(e.getMessage());
        }
    }

    enum WixTool {
        CANDLE,
        LIGHT;

        String getPath() {
            for (var dir : findWixInstallDirs()) {
                Path path = dir.resolve(name().toLowerCase(Locale.ROOT) + ".exe");
                if (Files.exists(path)) {
                    return path.toString();
                }
            }
            Logger.logSevere(name() + "not found. Please make sure to install Wix Toolset (https://wixtoolset.org/) before proceeding.");
            throw new RuntimeException("Wix Toolset not found");
        }

        private List<Path> findWixInstallDirs() {
            PathMatcher wixInstallDirMatcher = FileSystems.getDefault().getPathMatcher(
                    "glob:WiX Toolset v*");

            Path programFiles = getSystemDir("ProgramFiles", "\\Program Files");
            Path programFilesX86 = getSystemDir("ProgramFiles(x86)",
                    "\\Program Files (x86)");

            // Returns list of WiX install directories ordered by WiX version number.
            // Newer versions go first.
            return Stream.of(programFiles, programFilesX86).map(path -> {
                        List<Path> result;
                        try (var paths = Files.walk(path, 1)) {
                            result = paths.collect(Collectors.toList());
                        } catch (IOException ex) {
                            Logger.logDebug(ex.getMessage());
                            result = Collections.emptyList();
                        }
                        return result;
                    }).flatMap(List::stream)
                    .filter(path -> wixInstallDirMatcher.matches(path.getFileName()))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .map(path -> path.resolve("bin"))
                    .collect(Collectors.toList());
        }

        private Path getSystemDir(String envVar, String knownDir) {
            return Optional
                    .ofNullable(getEnvVariableAsPath(envVar))
                    .orElseGet(() -> Optional
                            .ofNullable(getEnvVariableAsPath("SystemDrive"))
                            .orElseGet(() -> Path.of("C:")).resolve(knownDir));
        }

        private Path getEnvVariableAsPath(String envVar) {
            String path = System.getenv(envVar);
            if (path != null) {
                try {
                    return Path.of(path);
                } catch (InvalidPathException ex) {
                    Logger.logDebug(MessageFormat.format("Invalid value of {0} environment variable", envVar));
                }
            }
            return null;
        }
    }
}
