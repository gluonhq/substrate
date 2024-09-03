/*
 * Copyright (c) 2019, 2024, Gluon
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
import com.gluonhq.substrate.util.ios.Simulator;

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

    private static final List<String> iosAdditionalDummySourceFiles = List.of("dummy.c");
    private static final List<String> iosAdditionalSourceFiles = Arrays.asList(
            "AppDelegate.m", "JvmFuncsFallbacks.c");

    private static final List<String> ioslibs = Arrays.asList("-lpthread", "-lz");

    private static final List<String> javafxLibs = Arrays.asList(
            "prism_es2", "glass", "javafx_font", "prism_common", "javafx_iio");
    private static final String javafxWebLib = "webview";

    private static final List<String> iosFrameworks = Arrays.asList(
            "Foundation", "UIKit", "CoreGraphics", "MobileCoreServices",
            "OpenGLES", "CoreText", "QuartzCore", "ImageIO",
            "CoreBluetooth", "CoreImage", "CoreLocation", "CoreMedia", "CoreMotion", "CoreVideo",
            "Accelerate", "AVFoundation", "AudioToolbox", "MediaPlayer", "UserNotifications",
            "ARKit", "AVKit", "SceneKit", "StoreKit", "ModelIO", "WebKit"
    );

    private static final String[] capFiles = {"AArch64LibCHelperDirectives.cap",
            "AMD64LibCHelperDirectives.cap", "BuiltinDirectives.cap",
            "JNIHeaderDirectives.cap", "JNIHeaderDirectivesJDK22OrLater.cap", "LibFFIHeaderDirectives.cap",
            "LLVMDirectives.cap", "PosixDirectives.cap", "RISCV64LibCHelperDirectives.cap"};
    private static final String capLocation= "/native/ios/cap/";
    private static final String iosCheck = "ios/check";

    public IosTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration ) {
        super(paths, configuration);

        if (!isSimulator() && projectConfiguration.getBackend() == null) {
            projectConfiguration.setBackend(Constants.BACKEND_LIR);
        }

        if (isSimulator() && projectConfiguration.usesJDK11()) {
            throw new RuntimeException("Error: the iOS simulator requires JDK 17");
        }
    }

    @Override
    List<String> getOtherStaticLibs() {
        return List.of("stdc++");
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        List<String> linkFlags = new ArrayList<>(Arrays.asList("-w", "-fPIC",
                "-arch", getTargetArch(),
                "-mios-version-min=11.0",
                "-isysroot", getSysroot()));
        if (projectConfiguration.isSharedLibrary()) {
            linkFlags.addAll(Arrays.asList(
                    "-shared",
                    "-undefined",
                    "dynamic_lookup"));
        }
        if (useJavaFX) {
            String javafxSDK = projectConfiguration.getJavafxStaticLibsPath().toString();
            List<String> libs = new ArrayList<>(javafxLibs);
            if (projectConfiguration.hasWeb()) {
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
        List<String> flags = new ArrayList<>(Arrays.asList("-xobjective-c",
                "-arch", getTargetArch(),
                "-mios-version-min=11.0",
                "-I" + projectConfiguration.getGraalPath().resolve("include").toString(),
                "-I" + projectConfiguration.getGraalPath().resolve("include").resolve("darwin").toString(),
                "-isysroot", getSysroot()));
        if (isSimulator()) {
            flags.add("-DGVM_IOS_SIM");
        }
        return flags;
    }

    List<String> getTargetSpecificAOTCompileFlags() throws IOException {
        List<String> flags = new ArrayList<>(Arrays.asList(
                "-H:PageSize=16384",
                "-Dsvm.targetName=iOS",
                "-Dsvm.targetArch=" + getTargetArch(),
                "-H:+UseCAPCache",
                "-H:CAPCacheDir=" + getCapCacheDir().toAbsolutePath().toString()));
        if (!isSimulator()) {
            flags.add("-H:CompilerBackend=" + projectConfiguration.getBackend());
            if (projectConfiguration.isUseLLVM()) {
                flags.add("-H:-SpawnIsolates");
            }
        }
        return flags;
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
        List<String> answer = new ArrayList<>(iosAdditionalDummySourceFiles);
        if (!projectConfiguration.isSharedLibrary() && !projectConfiguration.isStaticLibrary()) {
            answer.addAll(iosAdditionalSourceFiles);
        }
        return answer;
    }

    @Override
    List<String> getTargetSpecificLinkOutputFlags() {
        if (projectConfiguration.isSharedLibrary()) {
            return Arrays.asList("-o", getSharedLibPath().toString());
        }
        return super.getTargetSpecificLinkOutputFlags();
    }

    @Override
    List<String> copyAdditionalSourceFiles(Path workDir) throws IOException {
        String runtimeArgs = "";
        List<String> runtimeArgsList = projectConfiguration.getRuntimeArgsList();
        if (runtimeArgsList != null) {
            runtimeArgs = runtimeArgsList.stream()
                    .map((s -> "\"" + s + "\""))
                    .collect(Collectors.joining(",\n "));
        }
        List<String> files = new ArrayList<>();
        for (String fileName : getAdditionalSourceFiles()) {
            Path resource = FileOps.copyResource(getAdditionalSourceFileLocation() + fileName, workDir.resolve(fileName));
            if ("AppDelegate.m".equals(fileName)) {
                FileOps.replaceInFile(resource, "// USER_RUNTIME_ARGS", runtimeArgs);
            }
            files.add(fileName);
        }
        return files;
    }

    @Deprecated
    @Override
    List<String> getTargetSpecificObjectFiles() throws IOException {
        if (isSimulator() || !projectConfiguration.isUseLLVM()) {
            return super.getTargetSpecificObjectFiles();
        }
        return FileOps.findFile(paths.getGvmPath(), "llvm.o")
                .map(objectFile -> Collections.singletonList(objectFile.toAbsolutePath().toString()))
                .orElseThrow();
    }

    @Override
    String getLinker() {
        return "clang";
    }

    @Override
    List<String> getTargetSpecificNativeLibsFlags(Path libPath, List<String> libs) {
        return libs.stream()
                .map(s -> "-Wl,-force_load," + libPath.resolve(s))
                .collect(Collectors.toList());
    }

    @Override
    public boolean packageApp() throws IOException, InterruptedException {
        Path appPath = paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app");
        if (!Files.exists(appPath)) {
            throw new IOException("Error: " + appPath + " not found. Make sure you call link first.");
        }
        Logger.logInfo("Building .app bundle at: " + appPath);
        createInfoPlist(paths);

        if (!isSimulator() && !projectConfiguration.getReleaseConfiguration().isSkipSigning()) {
            CodeSigning codeSigning = new CodeSigning(paths, projectConfiguration);
            if (!codeSigning.signApp()) {
                throw new RuntimeException("Error signing the app");
            }
        }
        Logger.logInfo("The .app bundle was created successfully at: " + appPath);
        if (isSimulator()) {
            return true;
        }

        // ipa bundle
        Logger.logInfo("Building .ipa for " + appPath);

        Path tmpAppWrapper = paths.getTmpPath().resolve("tmpApp");
        Path tmpAppPayload = tmpAppWrapper.resolve("Payload");
        Files.createDirectories(tmpAppPayload);

        ProcessRunner runner = new ProcessRunner("cp", "-Rp", appPath.toString(), tmpAppPayload.toString());
        if (runner.runProcess("cp") != 0) {
            Logger.logInfo("Error copying app to " + tmpAppPayload);
            return false;
        }

        String target = paths.getAppPath().resolve(projectConfiguration.getAppName() + ".ipa").toString();

        runner = new ProcessRunner("zip", "--symlinks", "--recurse-paths", target, ".");
        if (runner.runProcess("zip", tmpAppWrapper.toFile()) != 0) {
            Logger.logInfo("Error zipping " + tmpAppWrapper);
            return false;
        }
        Logger.logInfo("The .ipa bundle was created successfully at: " + target);
        return true;
    }

    @Override
    public boolean install() throws IOException, InterruptedException {
        if (!isSimulator() && projectConfiguration.getReleaseConfiguration().isSkipSigning()) {
            // Without signing app can't be installed on device
            // Simply exit and do nothing
            return true;
        }

        Path app = getAndValidateAppPath();

        Deploy deploy = new Deploy(paths.getTmpPath().resolve(iosCheck));
        deploy.addDebugSymbolInfo(paths.getAppPath(), projectConfiguration.getAppName());
        if (isSimulator()) {
            Simulator simulator = new Simulator(paths, projectConfiguration);
            return simulator.installApp();
        }

        if (!deploy.install(app.toString())) {
            Logger.logInfo("Installing app " + app + " failed!");
            return false;
        }
        Logger.logInfo("App was installed successfully");
        return true;
    }

    @Override
    public boolean runUntilEnd() throws IOException, InterruptedException {
        if (!isSimulator() && projectConfiguration.getReleaseConfiguration().isSkipSigning()) {
            // Without signing app can't be installed or run on device
            // Simply exit and do nothing
            return true;
        }

        Path app = getAndValidateAppPath();

        Deploy deploy = new Deploy(paths.getTmpPath().resolve(iosCheck));
        if (isSimulator()) {
            Simulator simulator = new Simulator(paths, projectConfiguration);
            simulator.launchSimulator();
            return true;
        }
        String bundleId = InfoPlist.getBundleId(app.resolve(Constants.PLIST_FILE), projectConfiguration.getAppId());
        if (!deploy.run(app.toString(), bundleId)) {
            Logger.logInfo("Running " + bundleId + " failed!");
            return false;
        }
        Logger.logInfo("App was launched successfully");
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

    private void createInfoPlist(ProcessPaths paths) throws IOException, InterruptedException {
        InfoPlist infoPlist = new InfoPlist(paths, projectConfiguration, isSimulator() ?
                XcodeUtils.SDKS.IPHONESIMULATOR : XcodeUtils.SDKS.IPHONEOS);
        Path plist = infoPlist.processInfoPlist();
        if (plist != null) {
            Logger.logDebug("Plist at " + plist.toString());
            FileOps.copyStream(new FileInputStream(plist.toFile()),
                    paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app").resolve(Constants.PLIST_FILE));
        }
    }

    private Path getAndValidateAppPath() throws IOException, InterruptedException {
        Path app = paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app");
        if (!Files.exists(app)) {
            throw new IOException("Application not found at path " + app + ". Make sure you call link first.");
        }
        if (!Files.exists(app.resolve(projectConfiguration.getAppName()))) {
            throw new IOException("Native image not found at path " + app + ". Make sure you call link first.");
        }
        if (!Files.exists(app.resolve(Constants.PLIST_FILE))) {
            throw new IOException("Plist not found at path " + app + ". Make sure you call link and package first.");
        }
        if (!isSimulator() && !CodeSigning.verifyCodesign(app)) {
            throw new IOException("Codesign failed verifying the app " + app + ". Make sure you call link and package first.");
        }
        return app;
    }

    private boolean lipoMatch(Path path) {
        try {
            String lp = lipoInfo(path);
            if (lp == null) return false;
            return lp.indexOf(getTargetArch()) > 0;
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

    @Override
    Path getSharedLibPath() {
        return paths.getAppPath().resolve(getLinkOutputName() + ".dylib");
    }

}
