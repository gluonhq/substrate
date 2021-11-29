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

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;
import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.ClassPath;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.ReleaseConfiguration;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.XcodeUtils;
import com.gluonhq.substrate.util.plist.NSDictionaryEx;
import com.gluonhq.substrate.util.plist.NSObjectEx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.gluonhq.substrate.Constants.META_INF_SUBSTRATE_MACOS;
import static com.gluonhq.substrate.Constants.PARTIAL_PLIST_FILE;
import static com.gluonhq.substrate.model.ReleaseConfiguration.DEFAULT_BUNDLE_SHORT_VERSION;
import static com.gluonhq.substrate.model.ReleaseConfiguration.DEFAULT_BUNDLE_VERSION;

public class InfoPlist {

    private static final List<String> iconAssets = new ArrayList<>(Arrays.asList(
            "icon_128@1x.png", "icon_128@2x.png", "icon_16@1x.png", "icon_16@2x.png",
            "icon_256@1x.png", "icon_256@2x.png", "icon_32@1x.png", "icon_32@2x.png",
            "icon_512@1x.png", "icon_512@2x.png"
    ));

    private final XcodeUtils.SDKS sdk;
    private final InternalProjectConfiguration projectConfiguration;
    private final ProcessPaths paths;
    private final String sourceOS;
    private final XcodeUtils xcodeUtil;

    private final Path appPath;
    private final Path rootPath;
    private final Path partialPListDir;

    public InfoPlist(ProcessPaths paths, InternalProjectConfiguration projectConfiguration, XcodeUtils.SDKS sdk) throws IOException {
        this.paths = Objects.requireNonNull(paths);
        this.projectConfiguration = Objects.requireNonNull(projectConfiguration);
        this.sourceOS = projectConfiguration.getTargetTriplet().getOs();
        this.sdk = sdk;
        this.xcodeUtil = new XcodeUtils(sdk);
        appPath = paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app").resolve("Contents");
        rootPath = paths.getSourcePath().resolve(sourceOS);
        partialPListDir = paths.getTmpPath().resolve("partial-plists");
    }

    public Path processInfoPlist() throws IOException, InterruptedException {
        String appName = projectConfiguration.getAppName();
        String executableName = getExecutableName(appName, sourceOS);
        String bundleIdName = getBundleId(getPlistPath(paths, sourceOS), projectConfiguration.getAppId());
        ReleaseConfiguration releaseConfiguration = projectConfiguration.getReleaseConfiguration();
        String bundleName = Objects.requireNonNullElse(releaseConfiguration.getBundleName(), appName);
        String bundleVersion = Objects.requireNonNullElse(releaseConfiguration.getBundleVersion(), DEFAULT_BUNDLE_VERSION);
        String bundleShortVersion = Objects.requireNonNullElse(releaseConfiguration.getBundleShortVersion(), DEFAULT_BUNDLE_SHORT_VERSION);

        Path userPlist = rootPath.resolve(Constants.MACOS_PLIST_FILE);
        boolean inited = true;
        if (!Files.exists(userPlist)) {
            // copy plist to gensrc/macos
            Path genPlist = paths.getGenPath().resolve(sourceOS).resolve(Constants.MACOS_PLIST_FILE);
            Logger.logDebug("Copy " + Constants.MACOS_PLIST_FILE + " to " + genPlist.toString());
            FileOps.copyResource("/native/macosx/assets/Info.plist", genPlist);
            inited = false;
            Logger.logInfo("Default macOS plist generated in " + genPlist.toString() + ".\n" +
                    "Consider copying it to " + rootPath.toString() + " before performing any modification");
        }

        Path plist = getPlistPath(paths, sourceOS);
        if (plist == null) {
            throw new IOException("Error: plist not found");
        }

        Path userAssets = rootPath.resolve(Constants.MACOS_ASSETS_FOLDER);
        Path iconsetPath = userAssets.resolve("AppIcon.iconset");
        if (!Files.exists(iconsetPath) || !(Files.isDirectory(iconsetPath) && Files.list(iconsetPath).count() > 0)) {
            // copy assets to gensrc/macos
            Path macosPath = paths.getGenPath().resolve(sourceOS);
            Path genIconsetPath = macosPath.resolve(Constants.MACOS_ASSETS_FOLDER).resolve("AppIcon.iconset");
            iconAssets.forEach(a -> {
                try {
                    FileOps.copyResource("/native/macosx/assets/AppIcon.iconset/" + a, genIconsetPath.resolve(a));
                } catch (IOException e) {
                    Logger.logFatal(e, "Error copying resource " + a + ": " + e.getMessage());
                }
            });
            createIcns(genIconsetPath);
            Logger.logInfo("Default macOS resources generated in " + macosPath.toString() + ".\n" +
                    "Consider copying them to " + rootPath.toString() + " before performing any modification");
        } else {
            createIcns(iconsetPath);
        }

        Path executable = appPath.resolve("MacOS").resolve(executableName);
        if (!Files.exists(executable)) {
            String errorMessage = "The executable " + executable + " doesn't exist.";
            if (!appName.equals(executableName) && Files.exists(appPath.resolve("MacOS").resolve(appName))) {
                errorMessage += "\nMake sure the CFBundleExecutable key in the " + plist.toString() + " file is set to: " + appName;
            }
            throw new IOException(errorMessage);
        }
        if (!Files.isExecutable(executable)) {
            throw new IOException("The file " + executable + " is not executable.");
        }

        Logger.logDebug("Copy " + Constants.MACOS_PKGINFO_FILE + " to " + appPath.toString());
        FileOps.copyResource("/native/macosx/assets/" + Constants.MACOS_PKGINFO_FILE, appPath.resolve(Constants.MACOS_PKGINFO_FILE));

        copyPartialPlistFiles();

        try {
            NSDictionaryEx dict = new NSDictionaryEx(plist.toFile());
            if (!inited) {
                dict.put("CFBundleIdentifier", bundleIdName);
                dict.put("CFBundleExecutable", executableName);
                dict.put("CFBundleName", bundleName);
                dict.put("CFBundleVersion", bundleVersion);
                dict.put("CFBundleShortVersionString", bundleShortVersion);
                dict.saveAsXML(plist);
            } else {
                boolean modified = false;
                if (!bundleName.equals(appName) && !bundleName.equals(dict.get("CFBundleName").toString())) {
                    dict.put("CFBundleName", bundleName);
                    modified = true;
                }
                if (!bundleVersion.equals(DEFAULT_BUNDLE_VERSION) && !bundleVersion.equals(dict.get("CFBundleVersion").toString())) {
                    dict.put("CFBundleVersion", bundleVersion);
                    modified = true;
                }
                if (!bundleShortVersion.equals(DEFAULT_BUNDLE_VERSION) && !bundleShortVersion.equals(dict.get("CFBundleShortVersionString").toString())) {
                    dict.put("CFBundleShortVersionString", bundleShortVersion);
                    modified = true;
                }
                if (modified) {
                    Logger.logDebug("Updating " + plist.toString() + " with new values from releaseConfiguration");
                    dict.saveAsXML(plist);
                }
            }
            dict.put("DTPlatformName", xcodeUtil.getPlatformName());
            dict.put("DTSDKName", xcodeUtil.getSDKName());
            dict.put("CFBundleSupportedPlatforms", new NSArray(new NSString(sdk.getSdkName())));
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
                Files.walk(partialPListDir, 1)
                        .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".plist"))
                        .sorted((p1, p2) -> {
                            // classes.plist should be the last one, to override previous plist files
                            if (p1.toString().endsWith("classes_" + PARTIAL_PLIST_FILE)) {
                                return 1;
                            } else if (p2.toString().endsWith("classes_" + PARTIAL_PLIST_FILE)) {
                                return -1;
                            }
                            return p1.compareTo(p2);
                        })
                        .forEach(path -> {
                            try {
                                NSDictionary d = (NSDictionary) PropertyListParser.parse(path.toFile());
                                d.keySet().forEach(k -> orderedDict.put(k, d.get(k)));
                            } catch (Exception e) {
                                Logger.logFatal(e, "Error parsing plist file: " + path);
                            }
                        });
            }
            orderedDict.saveAsXML(appPath.resolve("Info.plist"));
            return plist;
        } catch (Exception ex) {
            Logger.logFatal(ex, "Could not process property list");
        }
        return null;
    }

    static Path getPlistPath(ProcessPaths paths, String sourceName) {
        Path userPlist = Objects.requireNonNull(paths).getSourcePath()
                .resolve(Objects.requireNonNull(sourceName)).resolve(Constants.MACOS_PLIST_FILE);
        if (Files.exists(userPlist)) {
            return userPlist;
        }
        Path genPlist = paths.getGenPath().resolve(sourceName).resolve(Constants.MACOS_PLIST_FILE);
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
                "Please check the src/macos/Default-info.plist file and make sure CFBundleExecutable key exists");
    }

    static String getBundleId(Path plist, String appId) {
        if (plist == null) {
            Objects.requireNonNull(appId, "AppId can't be null if plist is not provided");
            return appId;
        }

        try {
            String bundleID = new NSObjectEx(plist).getValueFromDictionary("CFBundleIdentifier");
            if (bundleID == null) {
                Logger.logSevere("Error: no bundleId was found");
                throw new RuntimeException("No bundleId was found.\n " +
                        "Please check the src/macos/Default-info.plist file and make sure CFBundleIdentifier key exists");
            }
            return bundleID;
        } catch (Exception ex) {
            Logger.logFatal(ex, "Could not find CFBundleIdentifier");
        }
        return null;
    }

    /**
     * Walks through the classes jar and other dependency jars files in the classpath,
     * and looks for META-INF/substrate/macos/Partial-Info.plist files.
     *
     * The method will copy all the plist files found into the partial plist folder
     *
     * @throws IOException
     */
    private void copyPartialPlistFiles() throws IOException, InterruptedException {
        if (!Files.exists(partialPListDir)) {
            Files.createDirectories(partialPListDir);
        }

        Logger.logDebug("Scanning for plist files");
        final List<File> jars = new ClassPath(projectConfiguration.getClasspath()).getJars(true);
        String prefix = META_INF_SUBSTRATE_MACOS + PARTIAL_PLIST_FILE;
        for (File jar : jars) {
            try (ZipFile zip = new ZipFile(jar)) {
                Logger.logDebug("Scanning " + jar);
                for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                    ZipEntry zipEntry = e.nextElement();
                    String name = zipEntry.getName();
                    if (!zipEntry.isDirectory() && name.equals(prefix)) {
                        String jarName = jar.getName().substring(0, jar.getName().lastIndexOf(".jar"));
                        Path classPath = partialPListDir.resolve(jarName + "_" + PARTIAL_PLIST_FILE);
                        Logger.logDebug("Adding plist from " + zip.getName() + " :: " + name + " into " + classPath);
                        FileOps.copyStream(zip.getInputStream(zipEntry), classPath);
                    }
                }
            } catch (IOException e) {
                throw new IOException("Error processing partial plist files from jar: " + jar + ": " + e.getMessage() + ", " + Arrays.toString(e.getSuppressed()));
            }
        }
    }

    private void createIcns(Path iconsetPath) throws IOException, InterruptedException {
        if (iconsetPath == null || !Files.exists(iconsetPath)) {
            throw new RuntimeException("Error: invalid path for iconset: " + iconsetPath);
        }

        ProcessRunner args = new ProcessRunner("iconutil", "-c", "icns", iconsetPath.toString());
        int result = args.runProcess("icon");
        if (result != 0) {
            throw new RuntimeException("Error creating AppIcon.icns");
        }
        Logger.logDebug("Copy AppIcon.icns to " + appPath.resolve("Resources").resolve("AppIcon.icns"));
        FileOps.copyFile(iconsetPath.getParent().resolve("AppIcon.icns"), appPath.resolve("Resources").resolve("AppIcon.icns"));
    }

}
