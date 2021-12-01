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
package com.gluonhq.substrate.target;

import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
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
import java.util.stream.Collectors;

public class MacOSTargetConfiguration extends DarwinTargetConfiguration {

    private static final String MIN_MACOS_VERSION = "10.12";

    private static final List<String> javaDarwinLibs = Arrays.asList("pthread", "z", "dl", "stdc++");
    private static final List<String> javaDarwinFrameworks = Arrays.asList("Foundation", "AppKit");

    private static final List<String> javaFxDarwinLibs = Arrays.asList("objc");
    private static final List<String> javaFxDarwinFrameworks = Arrays.asList(
            "ApplicationServices", "OpenGL", "QuartzCore", "Security", "Accelerate"
    );

    private static final List<String> staticJavaLibs = Arrays.asList(
            "java", "nio", "zip", "net", "prefs", "j2pkcs11", "fdlibm", "sunec", "extnet"
    );
    private static final List<String> staticJvmLibs = Arrays.asList(
            "jvm", "libchelper", "darwin"
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

    public MacOSTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration ) {
        super(paths, configuration);
    }

    @Override
    String getAdditionalSourceFileLocation() {
        return "/native/macosx/";
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        return Arrays.asList("AppDelegate.m", "launcher.c");
    }

    @Override
    List<String> getTargetSpecificCCompileFlags() {
        return Arrays.asList("-mmacosx-version-min=" + MIN_MACOS_VERSION);
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        List<String> linkFlags = new ArrayList<>(asListOfLibraryLinkFlags(javaDarwinLibs));

        linkFlags.add("-mmacosx-version-min=" + MIN_MACOS_VERSION);

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
    List<String> getStaticJavaLibs() {
        return staticJavaLibs;
    }

    @Override
    List<String> getOtherStaticLibs() {
        return staticJvmLibs;
    }

    @Override
    List<String> getTargetSpecificNativeLibsFlags(Path libPath, List<String> libs) {
        return libs.stream()
                .map(s -> "-Wl,-force_load," + libPath.resolve(s))
                .collect(Collectors.toList());
    }

    @Override
    public boolean link() throws IOException, InterruptedException {
        boolean result = super.link();

        if (result) {
            createInfoPlist(paths);

            if (!projectConfiguration.getReleaseConfiguration().isSkipSigning()) {
                CodeSigning codeSigning = new CodeSigning(paths, projectConfiguration);
                if (!codeSigning.signApp()) {
                    throw new RuntimeException("Error signing the app");
                }
            }
        }
        return result;
    }

    @Override
    public boolean packageApp() throws IOException, InterruptedException {
        Packager packager = new Packager(paths, projectConfiguration);
        return packager.createPackage(!projectConfiguration.getReleaseConfiguration().isSkipSigning());
    }

    @Override
    String getAppPath(String appName) {
        Path appPath = paths.getAppPath().resolve(appName + ".app").resolve("Contents").resolve("MacOS");
        if (!Files.exists(appPath)) {
            try {
                Files.createDirectories(appPath);
            } catch (IOException e) {
                Logger.logFatal(e, "Error creating path " + appPath);
            }
        }
        return appPath.toString() + "/" + appName;
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
}
