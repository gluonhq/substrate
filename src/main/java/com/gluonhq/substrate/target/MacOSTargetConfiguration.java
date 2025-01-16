/*
 * Copyright (c) 2019, 2025, Gluon
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
package com.gluonhq.substrate.target;

import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.XcodeUtils;
import com.gluonhq.substrate.util.macos.CodeSigning;
import com.gluonhq.substrate.util.macos.InfoPlist;
import com.gluonhq.substrate.util.macos.Packager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MacOSTargetConfiguration extends DarwinTargetConfiguration {

    public static final String MIN_MACOS_AMD64_VERSION = "11";
    public static final String MIN_MACOS_AARCH64_VERSION = "12";

    private static final List<String> javaDarwinLibs = Arrays.asList("pthread", "z", "dl", "stdc++");
    private static final List<String> javaDarwinFrameworks = Arrays.asList("Foundation", "AppKit");

    private static final List<String> javaFxDarwinLibs = Arrays.asList("objc");
    private static final List<String> javaFxDarwinFrameworks = Arrays.asList(
            "ApplicationServices", "OpenGL", "QuartzCore", "Security", "Accelerate", "Cocoa", "Carbon"
    );

    private static final List<String> staticJavaFxLibs = Arrays.asList(
            "glass", "javafx_font", "javafx_iio", "prism_es2"
    );
    private static final String staticWebKitLib = "jfxwebkit";
    private static final List<String> webKitDarwinLibs = List.of(
            "WebCore", "XMLJava", "JavaScriptCore", "bmalloc",
            "icui18n", "SqliteJava", "XSLTJava", "PAL", "WebCoreTestSupport",
            "WTF", "icuuc", "icudata"
    );
    private final String minVersion;

    public MacOSTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration) {
        super(paths, configuration);
        minVersion = Constants.ARCH_AMD64.equals(projectConfiguration.getTargetTriplet().getArch()) ? MIN_MACOS_AMD64_VERSION : MIN_MACOS_AARCH64_VERSION;
    }

    @Override
    String getAdditionalSourceFileLocation() {
        return "/native/macosx/";
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        if (projectConfiguration.isSharedLibrary()) {
            return List.of();
        }
        return Arrays.asList("AppDelegate.m", "launcher.c");
    }

    @Override
    List<String> getTargetSpecificLinkOutputFlags() {
        if (projectConfiguration.isSharedLibrary()) {
            return Arrays.asList("-o", getSharedLibPath().toString());
        }
        return super.getTargetSpecificLinkOutputFlags();
    }

    @Override
    List<String> getTargetSpecificCCompileFlags() {
        return Arrays.asList("-mmacosx-version-min=" + minVersion);
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        List<String> linkFlags = new ArrayList<>(asListOfLibraryLinkFlags(javaDarwinLibs));

        linkFlags.add("-mmacosx-version-min=" + minVersion);
        if (projectConfiguration.isSharedLibrary()) {
            linkFlags.addAll(Arrays.asList(
                    "-shared",
                    "-undefined",
                    "dynamic_lookup"));
        }
        if (useJavaFX) {
            linkFlags.addAll(asListOfLibraryLinkFlags(javaFxDarwinLibs));
            if (projectConfiguration.hasWeb()) {
                linkFlags.addAll(asListOfLibraryLinkFlags(webKitDarwinLibs));
            }
        }

        linkFlags.addAll(asListOfFrameworkLinkFlags(javaDarwinFrameworks));

        if (useJavaFX) {
            linkFlags.addAll(asListOfFrameworkLinkFlags(javaFxDarwinFrameworks));

            List<String> javafxLibs = new ArrayList<>(staticJavaFxLibs);
            if (usePrismSW) {
                javafxLibs.add("prism_sw");
            }
            if (projectConfiguration.hasWeb()) {
                javafxLibs.add(staticWebKitLib);
            }

            String staticLibPath = "-Wl,-force_load," + projectConfiguration.getJavafxStaticLibsPath() + "/";
            linkFlags.addAll(asListOfStaticLibraryLinkFlags(staticLibPath, javafxLibs));
        }

        return linkFlags;
    }

    @Override
    List<String> getTargetSpecificNativeLibsFlags(Path libPath, List<String> libs) {
        return libs.stream()
                .map(s -> "-Wl,-force_load," + libPath.resolve(s))
                .collect(Collectors.toList());
    }

    @Override
    Predicate<Path> getTargetSpecificNativeLibsFilter() {
        return FileOps::checkFileArchitecture;
    }

    @Override
    public boolean packageApp() throws IOException, InterruptedException {
        boolean sign = !projectConfiguration.getReleaseConfiguration().isSkipSigning();
        createAppBundle(sign);

        String packageType = projectConfiguration.getReleaseConfiguration().getPackageType();
        if (packageType == null || packageType.isEmpty()) {
            // if it is not set, do nothing
            return true;
        } else if (!("pkg".equals(packageType) || "dmg".equals(packageType))) {
            // if it is set but doesn't ask for pkg or dmg, fail
            Logger.logInfo("Error: packageType doesn't contain valid types for macOS");
            return false;
        }

        Packager packager = new Packager(paths, projectConfiguration);
        if ("pkg".equals(packageType)) {
            return packager.createPackage(sign);
        } else {
            return packager.createDmg(sign);
        }
    }

    private void createAppBundle(boolean sign) throws IOException, InterruptedException {
        String appName = projectConfiguration.getAppName();
        Path nativeImagePath = paths.getAppPath().resolve(appName);
        if (!Files.exists(nativeImagePath)) {
            throw new IOException("Error: " + nativeImagePath + " not found, run link first.");
        }
        Path bundlePath = paths.getAppPath().resolve(appName + ".app");
        Logger.logInfo("Building app bundle: " + bundlePath);

        if (Files.exists(bundlePath)) {
            FileOps.deleteDirectory(bundlePath);
        }

        Path appPath = bundlePath.resolve("Contents").resolve("MacOS");
        Files.createDirectories(appPath);
        FileOps.copyFile(nativeImagePath, appPath.resolve(appName));

        createInfoPlist(paths);

        if (sign) {
            CodeSigning codeSigning = new CodeSigning(paths, projectConfiguration);
            if (!codeSigning.signApp()) {
                throw new RuntimeException("Error signing the app");
            }
        }
        Logger.logInfo("App bundle built successfully at: " + bundlePath);
    }

    private void createInfoPlist(ProcessPaths paths) throws IOException, InterruptedException {
        InfoPlist infoPlist = new InfoPlist(paths, projectConfiguration, XcodeUtils.SDKS.MACOSX);
        Path plist = infoPlist.processInfoPlist();
        if (plist != null) {
            Logger.logDebug("Plist at " + plist.toString());
        }
    }

    private List<String> asListOfLibraryLinkFlags(List<String> libraries) {
        return libraries.stream()
                .map(library -> "-l" + library)
                .collect(Collectors.toList());
    }

    private List<String> asListOfStaticLibraryLinkFlags(String prefix, List<String> libraries) {
        return libraries.stream()
                .map(library -> prefix + "lib" + library + ".a")
                .collect(Collectors.toList());
    }

    private List<String> asListOfFrameworkLinkFlags(List<String> frameworks) {
        return frameworks.stream()
                .map(framework -> "-Wl,-framework," + framework)
                .collect(Collectors.toList());
    }

    @Override
    Path getSharedLibPath() {
        return paths.getAppPath().resolve(getLinkOutputName() + ".dylib");
    }
}
