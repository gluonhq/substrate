/*
 * Copyright (c) 2019, 2020, Gluon
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
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.XcodeUtils;
import com.gluonhq.substrate.util.ios.CodeSigning;
import com.gluonhq.substrate.util.ios.Deploy;
import com.gluonhq.substrate.util.ios.InfoPlist;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class IosTargetConfiguration extends DarwinTargetConfiguration {

    private List<String> iosAdditionalSourceFiles = Collections.singletonList("AppDelegate.m");

    private static final List<String> ioslibs = Arrays.asList(
            "-lpthread", "-llibchelper", "-lffi", "-ldarwin");

    private static final List<String> javafxLibs = Arrays.asList(
            "prism_es2", "glass", "javafx_font", "prism_common", "javafx_iio");
    private static final String javafxWebLib = "javafx_ios_webnode";

    private static final List<String> iosFrameworks = Arrays.asList(
            "Foundation", "UIKit", "CoreGraphics", "MobileCoreServices",
            "OpenGLES", "CoreText", "QuartzCore", "ImageIO",
            "CoreBluetooth", "CoreImage", "CoreLocation", "CoreMedia", "CoreMotion", "CoreVideo",
            "Accelerate", "AVFoundation", "AudioToolbox", "MediaPlayer", "UserNotifications",
            "ARKit", "AVKit", "SceneKit", "StoreKit"
    );

    private String[] capFiles = {"AArch64LibCHelperDirectives.cap",
            "AMD64LibCHelperDirectives.cap", "BuiltinDirectives.cap",
            "JNIHeaderDirectives.cap", "LibFFIHeaderDirectives.cap",
            "LLVMDirectives.cap", "PosixDirectives.cap"};
    private final String capLocation= "/native/ios/cap/";

    public IosTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration ) {
        super(paths, configuration);
    }

    @Override
    List<String> getTargetSpecificLinkLibraries() {
        List<String> defaultLinkFlags = new ArrayList<>(super.getTargetSpecificLinkLibraries());
        defaultLinkFlags.add("-lstdc++");
        return defaultLinkFlags;
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        List<String> linkFlags = new ArrayList<>(Arrays.asList("-w", "-fPIC",
                "-arch", getTargetArch(),
                "-mios-version-min=11.0",
                "-isysroot", getSysroot()));
        if (useJavaFX) {
            String javafxSDK = projectConfiguration.getJavafxStaticLibsPath().toString();
            List<String> libs = new ArrayList<>(javafxLibs);
            if (projectConfiguration.getClasspath().contains("javafx-web")) {
                libs.add(javafxWebLib);
            }
            libs.forEach(name ->
                    linkFlags.add("-Wl,-force_load," + javafxSDK + "/lib" + name + ".a"));
        }
        linkFlags.addAll(ioslibs);
        linkFlags.addAll(iosFrameworks.stream()
                .map(f -> "-Wl,-framework," + f)
                .collect(Collectors.toList()));
        return linkFlags;
    }

    @Override
    List<String> getTargetSpecificCCompileFlags() {
        return Arrays.asList("-xobjective-c",
                "-arch", getTargetArch(),
                "-mios-version-min=11.0",
                "-I"+projectConfiguration.getGraalPath().resolve("include").toString(),
                "-I"+projectConfiguration.getGraalPath().resolve("include").resolve("darwin").toString(),
                "-isysroot", getSysroot());
    }

    @Override
    List<String> getTargetSpecificAOTCompileFlags() throws IOException {
        return Arrays.asList("-H:CompilerBackend=" + Constants.BACKEND_LLVM,
                "-H:-SpawnIsolates",
                "-Dsvm.targetName=iOS",
                "-Dsvm.targetArch=" + getTargetArch(),
                "-H:+UseCAPCache",
                "-H:CAPCacheDir=" + getCapCacheDir().toAbsolutePath().toString());
    }

    @Override
    Predicate<Path> getTargetSpecificNativeLibsFilter() {
        return this::lipoMatch;
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
        return FileOps.findFile( paths.getGvmPath(), "llvm.o").map( objectFile ->
           Collections.singletonList(objectFile.toAbsolutePath().toString())
        ).orElseThrow();
    }

    @Override
    String getLinker() {
        return "clang";
    }

    @Override
    public boolean link() throws IOException, InterruptedException {
        boolean result = super.link();

        if (result) {
            createInfoPlist(paths);

            if (!isSimulator() && !projectConfiguration.getReleaseConfiguration().isSkipSigning()) {
                CodeSigning codeSigning = new CodeSigning(paths, projectConfiguration);
                if (!codeSigning.signApp()) {
                    throw new RuntimeException("Error signing the app");
                }
            }
        }
        return result;
    }

    @Override
    protected List<Path> getStaticJDKLibPaths() throws IOException {
        return Arrays.asList(fileDeps.getJavaSDKLibsPath());
    }

    @Override
    List<String> getTargetSpecificNativeLibsFlags(Path libPath, List<String> libs) {
        return libs.stream()
                .map(s -> "-Wl,-force_load," + libPath.resolve(s))
                .collect(Collectors.toList());
    }

    @Override
    public boolean runUntilEnd() throws IOException, InterruptedException {
        if (!isSimulator() && projectConfiguration.getReleaseConfiguration().isSkipSigning()) {
            // without signing, app can't be deployed
            return true;
        }

        Path app = paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app");
        if (!Files.exists(app)) {
            throw new IOException("Application not found at path " + app);
        }

        Deploy deploy = new Deploy();
        deploy.addDebugSymbolInfo(paths.getAppPath(), projectConfiguration.getAppName());
        if (isSimulator()) {
            // TODO: launchOnSimulator(appPath);
            return false;
        } else {
            return deploy.install(app.toString());
        }
    }

    @Override
    public boolean packageApp() throws IOException, InterruptedException {
        Path localAppPath = paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app");
        if (!Files.exists(localAppPath)) {
            throw new IOException("Error: " + projectConfiguration.getAppName() + ".app not found");
        }
        Logger.logDebug("Building IPA for " + localAppPath);

        Path tmpAppWrapper = paths.getTmpPath().resolve("tmpApp");
        Path tmpAppPayload = tmpAppWrapper.resolve("Payload");
        Files.createDirectories(tmpAppPayload);

        ProcessRunner runner = new ProcessRunner("cp", "-Rp", localAppPath.toString(), tmpAppPayload.toString());
        if (runner.runProcess("cp") != 0) {
            return false;
        }

        String target = paths.getAppPath().resolve(projectConfiguration.getAppName() + ".ipa").toString();
        Logger.logDebug("Creating IPA at " + target);

        runner = new ProcessRunner("zip", "--symlinks", "--recurse-paths", target, ".");
        return runner.runProcess("zip", tmpAppWrapper.toFile()) == 0;
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

    private String getTargetArch() {
        return projectConfiguration.getTargetTriplet().getArch();
    }

    private String getSysroot() {
        return isSimulator() ? XcodeUtils.SDKS.IPHONESIMULATOR.getSDKPath() :
                XcodeUtils.SDKS.IPHONEOS.getSDKPath();
    }

    private boolean isSimulator() {
        return Constants.ARCH_AMD64.equals(projectConfiguration.getTargetTriplet().getArch());
    }

    private void createInfoPlist(ProcessPaths paths) throws IOException {
        InfoPlist infoPlist = new InfoPlist(paths, projectConfiguration, isSimulator() ?
                XcodeUtils.SDKS.IPHONESIMULATOR : XcodeUtils.SDKS.IPHONEOS);
        Path plist = infoPlist.processInfoPlist();
        if (plist != null) {
            Logger.logDebug("Plist at " + plist.toString());
            FileOps.copyStream(new FileInputStream(plist.toFile()),
                    paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app").resolve(Constants.PLIST_FILE));
        }
    }

    private boolean lipoMatch(Path path) {
        try {
            return lipoInfo(path).indexOf(getTargetArch()) > 0;
        } catch (IOException | InterruptedException e) {
            Logger.logSevere("Error processing lipo for " + path);
        }
        return false;
    }

    private String lipoInfo(Path path) throws IOException, InterruptedException {
        return ProcessRunner.runProcessForSingleOutput("lipo", "lipo", "-info", path.toFile().getAbsolutePath());
    }

    /*
     * Copies the .cap files from the jar resource and store them in
     * a directory. Return that directory
     */
    private Path getCapCacheDir() throws IOException {
        Path capPath = paths.getGvmPath().resolve("capcache");
        if (!Files.exists(capPath)) {
            Files.createDirectory(capPath);
        }
        for (String cap : capFiles) {
            FileOps.copyResource(capLocation+cap, capPath.resolve(cap));
        }
        return capPath;
    }

}
