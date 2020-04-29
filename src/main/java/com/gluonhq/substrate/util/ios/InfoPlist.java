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

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;
import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.XcodeUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class InfoPlist {

    private static final List<String> assets = new ArrayList<>(Arrays.asList(
            "Default-375w-667h@2x~iphone.png", "Default-414w-736h@3x~iphone.png", "Default-portrait@2x~ipad.png",
            "Default-375w-812h-landscape@3x~iphone.png", "Default-568h@2x~iphone.png", "Default-portrait~ipad.png",
            "Default-375w-812h@3x~iphone.png", "Default-landscape@2x~ipad.png", "Default@2x~iphone.png",
            "Default-414w-736h-landscape@3x~iphone.png", "Default-414w-896h@3x~iphone.png", "Default-414w-896h-landscape@3x~iphone.png",
            "Default-landscape~ipad.png", "iTunesArtwork",
            "iTunesArtwork@2x"
    ));

    private static final List<String> iconAssets = new ArrayList<>(Arrays.asList(
            "Contents.json", "Gluon-app-store-icon-1024@1x.png", "Gluon-ipad-app-icon-76@1x.png", "Gluon-ipad-app-icon-76@2x.png",
            "Gluon-ipad-notifications-icon-20@1x.png", "Gluon-ipad-notifications-icon-20@2x.png",
            "Gluon-ipad-pro-app-icon-83.5@2x.png", "Gluon-ipad-settings-icon-29@1x.png", "Gluon-ipad-settings-icon-29@2x.png",
            "Gluon-ipad-spotlight-icon-40@1x.png","Gluon-ipad-spotlight-icon-40@2x.png","Gluon-iphone-app-icon-60@2x.png",
            "Gluon-iphone-app-icon-60@3x.png","Gluon-iphone-notification-icon-20@2x.png", "Gluon-iphone-notification-icon-20@3x.png",
            "Gluon-iphone-spotlight-icon-40@2x.png", "Gluon-iphone-spotlight-icon-40@3x.png", "Gluon-iphone-spotlight-settings-icon-29@2x.png",
            "Gluon-iphone-spotlight-settings-icon-29@3x.png"
    ));

    private final XcodeUtils.SDKS sdk;
    private final InternalProjectConfiguration projectConfiguration;
    private final ProcessPaths paths;
    private final String sourceOS;
    private final XcodeUtils xcodeUtil;

    private final Path appPath;
    private final Path rootPath;
    private final Path tmpPath;

    private Path partialPListDir;
    private String bundleId;
    private String minOSVersion = "11.0";

    public InfoPlist(ProcessPaths paths, InternalProjectConfiguration projectConfiguration, XcodeUtils.SDKS sdk) throws IOException {
        this.paths = Objects.requireNonNull(paths);
        this.projectConfiguration = Objects.requireNonNull(projectConfiguration);
        this.sourceOS = projectConfiguration.getTargetTriplet().getOs();
        this.sdk = sdk;
        this.xcodeUtil = new XcodeUtils(sdk);
        appPath = paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app");
        rootPath = paths.getSourcePath().resolve(sourceOS);
        tmpPath = paths.getTmpPath();
    }

    public Path processInfoPlist() throws IOException {
        String appName = projectConfiguration.getAppName();
        String executableName = getExecutableName(appName, sourceOS);
        String bundleIdName = getBundleId(getPlistPath(paths, sourceOS), projectConfiguration.getMainClassName());

        Path userPlist = rootPath.resolve(Constants.PLIST_FILE);
        boolean inited = true;
        if (!Files.exists(userPlist)) {
            // copy plist to gensrc/ios
            Path genPlist = paths.getGenPath().resolve(sourceOS).resolve(Constants.PLIST_FILE);
            Logger.logDebug("Copy " + Constants.PLIST_FILE + " to " + genPlist.toString());
            FileOps.copyResource("/native/ios/Default-Info.plist", genPlist);
            inited = false;
            Logger.logInfo("Default iOS plist generated in " + genPlist.toString() + ".\n" +
                    "Consider copying it to " + rootPath.toString() + " before performing any modification");
        }

        Path userAssets = rootPath.resolve(Constants.IOS_ASSETS_FOLDER);
        if (!Files.exists(userAssets) || !(Files.isDirectory(userAssets) && Files.list(userAssets).count() > 0)) {
            // copy assets to gensrc/ios
            Path iosPath = paths.getGenPath().resolve(sourceOS);
            Path iosAssets = iosPath.resolve(Constants.IOS_ASSETS_FOLDER);
            InfoPlist.assets.forEach(a -> {
                try {
                    FileOps.copyResource("/native/ios/assets/" + a, iosAssets.resolve(a));
                } catch (IOException e) {
                    Logger.logFatal(e, "Error copying resource " + a + ": " + e.getMessage());
                }
            });
            iconAssets.forEach(a -> {
                try {
                    FileOps.copyResource("/native/ios/assets/Assets.xcassets/AppIcon.appiconset/" + a,
                            iosAssets.resolve("Assets.xcassets").resolve("AppIcon.appiconset").resolve(a));
                } catch (IOException e) {
                    Logger.logFatal(e, "Error copying resource " + a + ": " + e.getMessage());
                }
            });
            FileOps.copyResource("/native/ios/assets/Assets.xcassets/Contents.json",
                    iosAssets.resolve("Assets.xcassets").resolve("Contents.json"));
            copyVerifyAssets(iosAssets);
            Logger.logInfo("Default iOS resources generated in " + iosPath.toString() + ".\n" +
                    "Consider copying them to " + rootPath.toString() + " before performing any modification");
        } else {
            copyVerifyAssets(userAssets);
            copyOtherAssets(userAssets);
        }

        Path plist = getPlistPath(paths, sourceOS);
        if (plist == null) {
            throw new IOException("Error: plist not found");
        }

        Path executable = appPath.resolve(executableName);
        if (!Files.exists(executable)) {
            String errorMessage = "The executable " + executable + " doesn't exist.";
            if (!appName.equals(executableName) && Files.exists(appPath.resolve(appName))) {
                errorMessage += "\nMake sure the CFBundleExecutable key in the " + plist.toString() + " file is set to: " + appName;
            }
            throw new IOException(errorMessage);
        }
        if (!Files.isExecutable(executable)) {
            throw new IOException("The file " + executable + " is not executable.");
        }

        try {
            NSDictionaryEx dict = new NSDictionaryEx(plist.toFile());
            if (! inited) {
                dict.put("CFBundleIdentifier", bundleIdName);
                dict.put("CFBundleExecutable", executableName);
                dict.put("CFBundleName", appName);
                dict.saveAsXML(plist);
            }
            dict.put("DTPlatformName", xcodeUtil.getPlatformName());
            dict.put("DTSDKName", xcodeUtil.getSDKName());
            dict.put("MinimumOSVersion", "11.0");
            dict.put("CFBundleSupportedPlatforms", new NSArray(new NSString("iPhoneOS")));
            dict.put("DTPlatformVersion", xcodeUtil.getPlatformVersion());
            dict.put("DTPlatformBuild", xcodeUtil.getPlatformBuild());
            dict.put("DTSDKBuild", xcodeUtil.getPlatformBuild());
            dict.put("DTXcode", xcodeUtil.getDTXcode());
            dict.put("DTXcodeBuild", xcodeUtil.getDTXcodeBuild());
            dict.put("BuildMachineOSBuild", xcodeUtil.getBuildMachineOSBuild());
            NSDictionaryEx orderedDict = new NSDictionaryEx();
            orderedDict.put("CFBundleVersion", dict.get("CFBundleVersion"));
            dict.remove("CFBundleVersion");
            dict.getKeySet().forEach(k -> orderedDict.put(k, dict.get(k)));

            if (partialPListDir != null) {
                Files.walk(partialPListDir)
                        .filter(f -> f.toString().endsWith(".plist"))
                        .forEach(f -> {
                            try {
                                NSDictionary d = (NSDictionary) PropertyListParser.parse(f.toFile());
                                d.keySet().forEach(k -> orderedDict.put(k, d.get(k)));
                            } catch (Exception e) {
                                Logger.logFatal(e, "Error reading plist");
                            }
                        });
            }
            orderedDict.put("MinimumOSVersion", minOSVersion != null ? minOSVersion : "11.0");

            //             BinaryPropertyListWriter.write(new File(appDir, "Info.plist"), orderedDict);
            orderedDict.saveAsBinary(appPath.resolve("Info.plist"));
            orderedDict.saveAsXML(tmpPath.resolve("Info.plist"));
            orderedDict.getEntrySet().forEach(e -> {
                        if ("CFBundleIdentifier".equals(e.getKey())) {
                            Logger.logDebug("Bundle ID = "+e.getValue().toString());
                            bundleId = e.getValue().toString();
                        }
                        Logger.logDebug("Info.plist Entry: " + e);
                    }
            );
            return plist;
        } catch (Exception ex) {
            Logger.logFatal(ex, "Could not process property list");
        }
        return null;
    }

    static Path getPlistPath(ProcessPaths paths, String sourceName) {
        Path userPlist = Objects.requireNonNull(paths).getSourcePath()
                .resolve(Objects.requireNonNull(sourceName)).resolve(Constants.PLIST_FILE);
        if (Files.exists(userPlist)) {
            return userPlist;
        }
        Path genPlist = paths.getGenPath().resolve(sourceName).resolve(Constants.PLIST_FILE);
        if (Files.exists(genPlist)) {
            return genPlist;
        }
        return null;
    }

    private String getExecutableName(String appName, String sourceName) {
        Path plist = getPlistPath(paths, sourceName);
        if (plist == null) {
            return appName;
        }

        try {
            NSDictionaryEx dict = new NSDictionaryEx(plist.toFile());
            return dict.getEntrySet().stream()
                    .filter(e -> "CFBundleExecutable".equals(e.getKey()))
                    .findFirst()
                    .map(e -> {
                        Logger.logDebug("Executable Name = " + e.getValue().toString());
                        return e.getValue().toString();
                    })
                    .orElseThrow(() -> new RuntimeException("CFBundleExecutable key was not found in plist file " + plist.toString()));
        } catch (Exception ex) {
            Logger.logFatal(ex, "Could not process CFBundleExecutable");
        }

        Logger.logSevere("Error: ExecutableName was found");
        throw new RuntimeException("No executable name was found.\n " +
                "Please check the src/ios/Default-info.plist file and make sure CFBundleExecutable key exists");
    }

    static String getBundleId(Path plist, String mainClassName) {
        if (plist == null) {
            String className = mainClassName;
            if (className.contains("/")) {
                className = className.substring(className.indexOf("/") + 1);
            }
            return className;
        }

        try {
            NSDictionaryEx dict = new NSDictionaryEx(plist.toFile());
            return dict.getEntrySet().stream()
                    .filter(e -> e.getKey().equals("CFBundleIdentifier"))
                    .findFirst()
                    .map(e -> {
                        Logger.logDebug("Got Bundle ID = " + e.getValue().toString());
                        return e.getValue().toString();
                    })
                    .orElseThrow(() -> new RuntimeException("CFBundleIdentifier key was not found in plist file " + plist.toString()));
        } catch (Exception ex) {
            Logger.logFatal(ex, "Could not process CFBundleIdentifier");
        }

        Logger.logSevere("Error: no bundleId was found");
        throw new RuntimeException("No bundleId was found.\n " +
                "Please check the src/ios/Default-info.plist file and make sure CFBundleIdentifier key exists");
    }

    private void copyVerifyAssets(Path resourcePath) throws IOException {
        if (resourcePath == null || !Files.exists(resourcePath)) {
            throw new RuntimeException("Error: invalid path " + resourcePath);
        }
        if (minOSVersion == null) {
            minOSVersion = "11.0";
        }
        Files.walk(resourcePath, 1).forEach(p -> {
            if (Files.isDirectory(p)) {
                if (p.toString().endsWith(".xcassets")) {
                    try {
                        Logger.logDebug("Calling verifyAssetCatalog for " + p.toString());
                        verifyAssetCatalog(p, sdk.name().toLowerCase(Locale.ROOT),
                                minOSVersion,
                                Arrays.asList("iphone", "ipad"), "");
                    } catch (Exception ex) {
                        Logger.logFatal(ex, "Failed creating directory " + p);
                    }
                }
            } else {
                Path targetPath = appPath.resolve(resourcePath.relativize(p));
                FileOps.copyFile(p, targetPath);
            }
        });
    }

    private void verifyAssetCatalog(Path resourcePath, String platform, String minOSVersion, List<String> devices, String output) throws Exception {
        List<String> commandsList = new ArrayList<>();
        commandsList.add("--output-format");
        commandsList.add("human-readable-text");

        final String appIconSet = ".appiconset";
        final String launchImage = ".launchimage";

        File inputDir = resourcePath.toFile();
        File outputDir = new File(appPath.toFile(), output);
        Files.createDirectories(outputDir.toPath());
        Files.walk(resourcePath).forEach(p -> {
            if (Files.isDirectory(p) && p.toString().endsWith(appIconSet)) {
                String appIconSetName = p.getFileName().toString()
                        .substring(0, p.getFileName().toString().length() - appIconSet.length());
                commandsList.add("--app-icon");
                commandsList.add(appIconSetName);
            } else if (Files.isDirectory(p) && p.toString().endsWith(launchImage)) {
                String launchImagesName = p.getFileName().toString()
                        .substring(0, p.getFileName().toString().length() - launchImage.length());
                commandsList.add("--launch-image");
                commandsList.add(launchImagesName);
            }
        });

        partialPListDir = tmpPath.resolve("partial-plists");
        if (Files.exists(partialPListDir)) {
            try {
                Files.walk(partialPListDir).forEach(f -> f.toFile().delete());
            } catch (IOException ex) {
                Logger.logSevere("Error removing files from " + partialPListDir.toString() + ": " + ex);
            }
        }
        try {
            Files.createDirectories(partialPListDir);
        } catch (IOException ex) {
            Logger.logSevere("Error creating " + partialPListDir.toString() + ": " + ex);
        }

        File partialInfoPlist = File.createTempFile(resourcePath.getFileName().toString() + "_", ".plist", partialPListDir.toFile());

        commandsList.add("--output-partial-info-plist");
        commandsList.add(partialInfoPlist.toString());

        commandsList.add("--platform");
        commandsList.add(platform);

        String actoolForSdk = XcodeUtils.getCommandForSdk("actool", platform);
        commandsList.add("--minimum-deployment-target");
        commandsList.add(minOSVersion);
        devices.forEach(device -> {
            commandsList.add("--target-device");
            commandsList.add(device);
        });

        ProcessRunner args = new ProcessRunner(actoolForSdk);
        args.addArgs(commandsList);
        args.addArgs("--compress-pngs", "--compile", outputDir.toString(), inputDir.toString());
        int result = args.runProcess("actool");
        if (result != 0) {
            throw new RuntimeException("Error verifyAssetCatalog");
        }
    }

    /**
     * Scans the src/ios/assets folder for possible folders other than
     * Assets.cassets (which is compressed with actool),
     * and copy them directly to the app folder
     * @param resourcePath the path for ios assets
     * @throws IOException
     */
    private void copyOtherAssets(Path resourcePath) throws IOException {
        if (resourcePath == null || !Files.exists(resourcePath)) {
            throw new RuntimeException("Error: invalid path " + resourcePath);
        }
        List<Path> otherAssets = Files.list(resourcePath)
                .filter(r -> Files.isDirectory(r))
                .filter(r -> !r.toString().endsWith("Assets.xcassets"))
                .collect(Collectors.toList());
        for (Path assetPath : otherAssets) {
            Path targetPath = appPath.resolve(resourcePath.relativize(assetPath));
            if (Files.exists(targetPath)) {
                FileOps.deleteDirectory(targetPath);
            }
            Logger.logDebug("Copying directory " + assetPath);
            FileOps.copyDirectory(assetPath, targetPath);
        }
    }

}
