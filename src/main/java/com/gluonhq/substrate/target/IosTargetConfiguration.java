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
package com.gluonhq.substrate.target;

import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.ProjectConfiguration;
import com.gluonhq.substrate.util.FileDeps;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.XcodeUtils;
import com.gluonhq.substrate.util.ios.CodeSigning;
import com.gluonhq.substrate.util.ios.Deploy;
import com.gluonhq.substrate.util.ios.InfoPlist;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IosTargetConfiguration extends AbstractTargetConfiguration {

    private List<String> iosAdditionalSourceFiles = Collections.singletonList("AppDelegate.m");

    private static final List<String> ioslibs = Arrays.asList(
            "-lpthread", "-lz", "-lstrictmath", "-llibchelper",
            "-ljava", "-lnio", "-lzip", "-lnet", "-ljvm");

    private static final List<String> javafxLibs = Arrays.asList(
            "prism_es2", "glass", "javafx_font", "prism_common", "javafx_iio");

    private static final List<String> iosFrameworks = Arrays.asList(
            "-Wl,-framework,Foundation", "-Wl,-framework,UIKit",
            "-Wl,-framework,CoreGraphics", "-Wl,-framework,MobileCoreServices",
            "-Wl,-framework,OpenGLES", "-Wl,-framework,CoreText",
            "-Wl,-framework,QuartzCore", "-Wl,-framework,ImageIO");

    @Override
    List<String> getTargetSpecificLinkLibraries() {
        List<String> defaultLinkFlags = new ArrayList<>(super.getTargetSpecificLinkLibraries());
        defaultLinkFlags.add("-lstdc++");
        return defaultLinkFlags;
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        List<String> linkFlags = new ArrayList<>(Arrays.asList("-w", "-fPIC",
                "-arch", Constants.ARCH_ARM64,
                "-mios-version-min=11.0",
                "-isysroot", getSysroot()));
        if (useJavaFX) {
            String javafxSDK = projectConfiguration.getJavafxStaticLibsPath().toString();
            javafxLibs.forEach(name ->
                    linkFlags.add("-Wl,-force_load," + javafxSDK + "/lib" + name + ".a"));
        }
        linkFlags.addAll(ioslibs);
        linkFlags.addAll(iosFrameworks);
        return linkFlags;
    }

    @Override
    List<String> getTargetSpecificCCompileFlags() {
        return Arrays.asList("-xobjective-c",
                "-arch", getArch(),
                "-isysroot", getSysroot());
    }

    @Override
    List<String> getTargetSpecificAOTCompileFlags() throws IOException {

        Path llcPath = getLlcPath();
        return Arrays.asList("-H:CompilerBackend=" + Constants.BACKEND_LLVM,
                "-H:-SpawnIsolates",
                "-Dsvm.targetName=iOS",
                "-Dsvm.targetArch=" + getArch(),
                "-H:CustomLLC=" + llcPath.toAbsolutePath().toString());
    }


    @Override
    public String getAdditionalSourceFileLocation() {
        return "/native/ios/";
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        return iosAdditionalSourceFiles;
    }

    @Override
    List<String> getTargetSpecificObjectFiles() throws IOException {
        Path gvmPath = paths.getGvmPath();
        Path objectFile = FileOps.findFile(gvmPath, "llvm.o");
        return Collections.singletonList(objectFile.toAbsolutePath().toString());
    }

    @Override
    String getLinker() {
        return "clang";
    }

    @Override
    public boolean link(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {
        boolean result = super.link(paths, projectConfiguration);

        if (result) {
            createInfoPlist(paths, projectConfiguration);

            if (!isSimulator() && !projectConfiguration.getIosSigningConfiguration().isSkipSigning()) {
                CodeSigning codeSigning = new CodeSigning(paths, projectConfiguration);
                if (!codeSigning.signApp()) {
                    throw new RuntimeException("Error signing the app");
                }
            }
        }
        return result;
    }

    @Override
    public boolean runUntilEnd(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {
        this.paths = paths;
        this.projectConfiguration = projectConfiguration;
        Deploy.addDebugSymbolInfo(paths.getAppPath(), projectConfiguration.getAppName());
        String appPath = paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app").toString();
        if (isSimulator()) {
            // TODO: launchOnSimulator(appPath);
            return false;
        } else if (!projectConfiguration.getIosSigningConfiguration().isSkipSigning()) {
            return Deploy.install(appPath);
        }
        return true;
    }

    @Override
    public String getCompiler() {
        return "clang";
    }

    @Override
    String getAppPath(String appName) {
        Path appPath = paths.getAppPath().resolve(appName + ".app");
        if (!Files.exists(appPath)) {
            try {
                Files.createDirectories(appPath);
            } catch (IOException e) {
                Logger.logFatal(e, "Error creating path " + appPath);
            }
        }
        return appPath.toString() + "/" + appName;
    }

    /**
     * If we are not using JavaFX, we immediately return the provided classpath, no further processing needed
     * If we use JavaFX, we will first obtain the location of the JavaFX SDK for this configuration.
     * This may throw an IOException.
     * After the path to the JavaFX SDK is obtained, the JavaFX jars for the host platform are replaced by
     * the JavaFX jars for the target platform.
     * @param classPath The provided classpath
     * @return A string with the modified classpath if JavaFX is used
     * @throws IOException
     */
    @Override
    String processClassPath(String classPath) throws IOException {
        if (!projectConfiguration.isUseJavaFX()) {
            return classPath;
        }
        Path javafxSDKLibsPath = FileDeps.getJavaFXSDKLibsPath(projectConfiguration);
        return Stream.of(classPath.split(File.pathSeparator))
                .map(s -> {
                    if (s.indexOf("javafx-graphics") > 0) {
                        return javafxSDKLibsPath.resolve("javafx.graphics.jar").toString();
                    } else if (s.indexOf("javafx-controls") > 0) {
                        return javafxSDKLibsPath.resolve("javafx.controls.jar").toString();
                    } else {
                        return s;
                    }
                })
                .collect(Collectors.joining(File.pathSeparator));
    }

    private String getArch() {
        return projectConfiguration.getTargetTriplet().getArch();
    }

    private String getSysroot() {
        return isSimulator() ? XcodeUtils.SDKS.IPHONESIMULATOR.getSDKPath() :
                XcodeUtils.SDKS.IPHONEOS.getSDKPath();
    }

    private boolean isSimulator() {
        return Constants.ARCH_AMD64.equals(projectConfiguration.getTargetTriplet().getArch());
    }

    private void createInfoPlist(ProcessPaths paths, ProjectConfiguration projectConfiguration) {
        try {
            InfoPlist infoPlist = new InfoPlist(paths, projectConfiguration, isSimulator() ?
                    XcodeUtils.SDKS.IPHONESIMULATOR : XcodeUtils.SDKS.IPHONEOS);
            Path plist = infoPlist.processInfoPlist();
            if (plist != null) {
                Logger.logDebug("Plist at " + plist.toString());
                FileOps.copyStream(new FileInputStream(plist.toFile()),
                        paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app").resolve(Constants.PLIST_FILE));
            }
        } catch (IOException e) {
            Logger.logFatal(e, "Error creating info.plist");
        }
    }
}
