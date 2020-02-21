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
import com.gluonhq.substrate.model.ClassPath;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.Version;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AndroidTargetConfiguration extends PosixTargetConfiguration {

    private final String ndk;
    private final String sdk;
    private final Path ldlld;
    private final Path clang;
    private final String hostPlatformFolder;

    private List<String> androidAdditionalSourceFiles = Collections.singletonList("launcher.c");
    private List<String> androidAdditionalHeaderFiles = Collections.singletonList("grandroid.h");
    private List<String> cFlags = Arrays.asList("-target", "aarch64-linux-android", "-I.");
    private List<String> linkFlags = Arrays.asList("-target", "aarch64-linux-android21", "-fPIC", "-Wl,--gc-sections",
            "-landroid", "-llog", "-lnet", "-shared", "-lffi");
    private List<String> javafxLinkFlags = Arrays.asList("-Wl,--whole-archive",
            "-lprism_es2_monocle", "-lglass_monocle", "-ljavafx_font_freetype", "-ljavafx_iio", "-Wl,--no-whole-archive",
            "-lGLESv2", "-lEGL", "-lfreetype");
    private String[] capFiles = {"AArch64LibCHelperDirectives.cap",
            "AMD64LibCHelperDirectives.cap", "BuiltinDirectives.cap",
            "JNIHeaderDirectives.cap", "LibFFIHeaderDirectives.cap",
            "LLVMDirectives.cap", "PosixDirectives.cap"};
    private final String capLocation= "/native/android/cap/";
    private final List<String> iconFolders = Arrays.asList("mipmap-hdpi",
            "mipmap-ldpi", "mipmap-mdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi");

    public AndroidTargetConfiguration( ProcessPaths paths, InternalProjectConfiguration configuration ) throws IOException {
        super(paths,configuration);
        
        this.sdk = fileDeps.getAndroidSDKPath().toString();
        this.ndk = fileDeps.getAndroidNDKPath().toString();
        this.hostPlatformFolder = configuration.getHostTriplet().getOs() + "-x86_64";

        Path ldguess = Paths.get(this.ndk, "toolchains", "llvm", "prebuilt", hostPlatformFolder, "bin", "ld.lld");
        this.ldlld = Files.exists(ldguess) ? ldguess : null; 
        
        Path clangguess = Paths.get(this.ndk, "toolchains", "llvm", "prebuilt", hostPlatformFolder, "bin", "clang");
        this.clang = Files.exists(clangguess) ? clangguess : null;
    }

    /**
     * // TODO: this is 100% similar to what we do on iOS. We need something like CrossPlatformTools for this.
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

        return new ClassPath(classPath).mapWithLibs(
                fileDeps.getJavaFXSDKLibsPath(), "javafx-graphics", "javafx-base", "javafx-controls");

    }

    @Override
    public boolean compile(String classPath) throws IOException, InterruptedException {
        // we override compile as we need to do some checks first. If we have no ld.lld in android_ndk, we should not start compiling
        if (ndk == null) throw new IOException ("Can't find an Android NDK on your system. Set the environment property ANDROID_NDK");
        if (ldlld == null) throw new IOException ("You specified an android ndk, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/"+hostPlatformFolder+"/bin/ldlld");
        if (clang == null) throw new IOException ("You specified an android ndk, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/"+hostPlatformFolder+"/bin/clang");
        return super.compile(classPath);
    }

    @Override
    public boolean link() throws IOException, InterruptedException {
        // we override link as we need to do some checks first. If we have no clang in android_ndk, we should not start linking
        if (ndk == null) throw new IOException ("Can't find an Android NDK on your system. Set the environment property ANDROID_NDK");
        if (clang == null) throw new IOException ("You specified an android ndk, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/"+hostPlatformFolder+"/bin/clang");
        if (sdk == null) throw new IOException ("Can't find an Android SDK on your system. Set the environment property ANDROID_SDK");

        boolean result = super.link();

        if (!result) {
            return false;
        }

        Path sdkPath = Paths.get(sdk);
        Path buildToolsPath = sdkPath.resolve("build-tools").resolve(findLatestBuildTool(sdkPath));

        String androidJar = sdkPath.resolve("platforms").resolve("android-27").resolve("android.jar").toString();
        String aaptCmd = buildToolsPath.resolve("aapt").toString();

        Path dalvikPath = paths.getGvmPath().resolve("dalvik");
        Files.createDirectories(dalvikPath);
        Path dalvikSrcPath = dalvikPath.resolve("src");
        Path dalvikClassPath = dalvikPath.resolve("class");
        Path dalvikBinPath = dalvikPath.resolve("bin");
        Path dalvikLibPath = dalvikPath.resolve("lib");
        Path dalvikLibArm64Path = dalvikLibPath.resolve("arm64-v8a");

        String unalignedApk = dalvikBinPath.resolve(projectConfiguration.getAppName()+".unaligned.apk").toString();
        String alignedApk = dalvikBinPath.resolve(projectConfiguration.getAppName()+".apk").toString();

        Files.createDirectories(dalvikSrcPath);
        Files.createDirectories(dalvikClassPath);
        Files.createDirectories(dalvikBinPath);
        Files.createDirectories(dalvikLibPath);
        Files.createDirectories(dalvikLibArm64Path);
        Path androidManifestPath = dalvikPath.resolve("AndroidManifest.xml");
        Path dalvikActivityPackage = dalvikClassPath.resolve("com/gluonhq/helloandroid");
        Path dalvikKeyCodePackage = dalvikClassPath.resolve("javafx/scene/input");
        FileOps.copyResource("/native/android/dalvik/MainActivity.class", dalvikActivityPackage.resolve("MainActivity.class"));
        FileOps.copyResource("/native/android/dalvik/MainActivity$1.class", dalvikActivityPackage.resolve("MainActivity$1.class"));
        FileOps.copyResource("/native/android/dalvik/MainActivity$InternalSurfaceView.class", dalvikActivityPackage.resolve("MainActivity$InternalSurfaceView.class"));
        FileOps.copyResource("/native/android/dalvik/KeyCode.class", dalvikKeyCodePackage.resolve("KeyCode.class"));
        FileOps.copyResource("/native/android/dalvik/KeyCode$KeyCodeClass.class", dalvikKeyCodePackage.resolve("KeyCode$KeyCodeClass.class"));
        FileOps.copyResource("/native/android/AndroidManifest.xml", dalvikPath.resolve("AndroidManifest.xml"));
        FileOps.replaceInFile(dalvikPath.resolve("AndroidManifest.xml"), "package='com.gluonhq.helloandroid'", "package='" + projectConfiguration.getAppId() + "'");
        FileOps.replaceInFile(dalvikPath.resolve("AndroidManifest.xml"), "A HelloGraal", projectConfiguration.getAppName());

        // resources
       for (String iconFolder : iconFolders) {
            Path assetPath = dalvikPath.resolve("res").resolve(iconFolder);
            Files.createDirectories(assetPath);
            FileOps.copyResource("/native/android/assets/res/" + iconFolder + "/ic_launcher.png", assetPath.resolve("ic_launcher.png"));
        }

        int processResult;

        ProcessRunner dx = new ProcessRunner(buildToolsPath.resolve("dx").toString(), "--dex",
                "--output="+dalvikBinPath.resolve("classes.dex"),dalvikClassPath.toString());
        processResult = dx.runProcess("DX");
        if (processResult != 0)
            return false;

        ProcessRunner aaptpackage = new ProcessRunner(aaptCmd, "package",
                "-f", "-m", "-F", unalignedApk,
                "-M", androidManifestPath.toString(),
                "-S", dalvikPath.resolve("res").toString(),
                "-I", androidJar);
        processResult = aaptpackage.runProcess("AAPT-package");
        if (processResult != 0)
            return false;

        ProcessRunner aaptAddClass = new ProcessRunner(aaptCmd, "add", unalignedApk,
                "classes.dex");
        processResult = aaptAddClass.runProcess("AAPT-add classes", dalvikBinPath.toFile());
        if (processResult != 0)
            return false;

        Path libPath = paths.getAppPath().resolve(projectConfiguration.getAppName());
        Path graalLibPath = dalvikLibArm64Path.resolve("libmygraal.so");
        Files.deleteIfExists(graalLibPath);
        Files.copy(libPath, graalLibPath);

        boolean useJavaFX = projectConfiguration.isUseJavaFX();
        if (useJavaFX) {
            Path freetypeLibPath = dalvikLibArm64Path.resolve("libfreetype.so");
            Files.deleteIfExists(freetypeLibPath);
            Files.copy(fileDeps.getJavaFXSDKLibsPath().resolve("libfreetype.so"), freetypeLibPath);
        }

        List<String> aaptAddLibsArgs = new ArrayList<>(Arrays.asList(aaptCmd, "add", unalignedApk,"lib/arm64-v8a/libmygraal.so"));
        if (useJavaFX) {
            aaptAddLibsArgs.add("lib/arm64-v8a/libfreetype.so");
        }

        ProcessRunner aaptAddLibs = new ProcessRunner(aaptAddLibsArgs.toArray(String[]::new));
        processResult = aaptAddLibs.runProcess("AAPT-add lib", dalvikPath.toFile());
        if (processResult != 0) return false;

        ProcessRunner zipAlign = new ProcessRunner(buildToolsPath.resolve("zipalign").toString(), "-f", "4", unalignedApk, alignedApk);
        processResult = zipAlign.runProcess("zipalign");
        if (processResult != 0)
            return false;

        createDevelopKeystore();

        ProcessRunner sign =  new ProcessRunner(buildToolsPath.resolve("apksigner").toString(), "sign", "--ks",
                Constants.USER_SUBSTRATE_PATH.resolve(Constants.ANDROID_KEYSTORE).toString(), "--ks-key-alias", "androiddebugkey", "--ks-pass", "pass:android", "--key-pass", "pass:android",  alignedApk);
        processResult = sign.runProcess("sign");
        
        return processResult == 0;
    }

    @Override
    public boolean runUntilEnd() throws IOException, InterruptedException {
        Path sdkPath = Paths.get(sdk);

        Path dalvikPath = paths.getGvmPath().resolve("dalvik");
        Path dalvikBinPath = dalvikPath.resolve("bin");
        Path apkPath = dalvikBinPath.resolve(projectConfiguration.getAppName()+".apk");
        if (!Files.exists(apkPath)) {
            throw new IOException("Application not found at path " + apkPath);
        }

        int processResult;

        // Path keystorePath = Paths.get(System.getProperty("user.home")+"/android.keystore");
        // if (!Files.exists(keystorePath)) throw new IOException ("Can't find android keystore file at "+System.getProperty("user.home"));

        // ProcessRunner sign =  new ProcessRunner(buildToolsPath.resolve("apksigner").toString(),"sign", "--ks",
        // keystorePath.toString() , alignedApk);
        // processResult = sign.runProcess("sign");
        // if (processResult != 0)
        //     return false;

        ProcessRunner install = new ProcessRunner(sdkPath.resolve("platform-tools").resolve("adb").toString(),
                "install", "-r", apkPath.toString());
        processResult = install.runProcess("install");
        if (processResult != 0) throw new IOException("Application instalation failed!");

        Runnable logcat = () -> {
            try {
                ProcessRunner clearLog = new ProcessRunner(sdkPath.resolve("platform-tools").resolve("adb").toString(),
                "logcat", "-c");
                clearLog.runProcess("clearLog");

                ProcessRunner log = new ProcessRunner(sdkPath.resolve("platform-tools").resolve("adb").toString(),
                "-d", "logcat", "-v", "brief", "-v", "color", "GraalCompiled:V", "GraalActivity:V", "GraalGluon:V", "AndroidRuntime:E", "ActivityManager:W", "*:S");
                log.setInfo(true);
                log.runProcess("log");
            } catch (IOException | InterruptedException e) { 
                e.printStackTrace(); 
            }
        };
        
        Thread logger = new Thread(logcat);
        logger.start();

        ProcessRunner run = new ProcessRunner(sdkPath.resolve("platform-tools").resolve("adb").toString(),
                "shell", "monkey", "-p", projectConfiguration.getAppId(), "1");
        processResult += run.runProcess("run");
        if (processResult != 0) throw new IOException("Application starting failed!");
        
        logger.join();
        return processResult == 0;
    }

    @Override
    boolean allowHttps() {
        return false;
    }

    @Override
    List<String> getTargetSpecificAOTCompileFlags() throws IOException {
        Path llcPath = getLlcPath();
        Path internalLlcPath = projectConfiguration.getGraalPath().resolve("lib").resolve("llvm").resolve("bin");
        
        return Arrays.asList("-H:CompilerBackend=" + Constants.BACKEND_LLVM,
                "-H:-SpawnIsolates",
                "-Dsvm.targetArch=" + projectConfiguration.getTargetTriplet().getArch(),
                "-H:+UseOnlyWritableBootImageHeap",
                "-H:+UseCAPCache",
                "-Dllvm.bin.dir=" + internalLlcPath,
                "-H:CAPCacheDir=" + getCapCacheDir().toAbsolutePath().toString(),
                "-H:CustomLD=" + ldlld.toAbsolutePath().toString(),
                "-H:CustomLLC=" + llcPath.toAbsolutePath().toString());
    }

    @Override
    List<String> getTargetSpecificObjectFiles() throws IOException {
       return FileOps.findFile( paths.getGvmPath(), "llvm.o").map( objectFile ->
           Collections.singletonList(objectFile.toAbsolutePath().toString())
       ).orElseThrow();
    }

    @Override
    public String getCompiler() {
        return clang.toAbsolutePath().toString();
    }

    @Override
    String getLinker() {
        return clang.toAbsolutePath().toString();
    }

    @Override
    List<String> getTargetSpecificCCompileFlags() {
        return cFlags;
    }


    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        if (!useJavaFX) return linkFlags;
        List<String> answer = new ArrayList<>();
        answer.addAll(linkFlags);
        answer.addAll(javafxLinkFlags);
        return answer;
    }

    @Override
    List<String> getTargetSpecificNativeLibsFlags(Path libPath, List<String> libs) {
        List<String> linkFlags = new ArrayList<>();
        linkFlags.add("-Wl,--whole-archive");
        linkFlags.addAll(libs.stream()
                .map(s -> libPath.resolve(s).toString())
                .collect(Collectors.toList()));
        linkFlags.add("-Wl,--no-whole-archive");
        return linkFlags;
    }

    @Override
    public String getAdditionalSourceFileLocation() {
        return "/native/android/c/";
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        return androidAdditionalSourceFiles;
    }

    @Override
    List<String> getAdditionalHeaderFiles() {
        return androidAdditionalHeaderFiles;
    }

    @Override
    boolean useGraalVMJavaStaticLibraries() {
        return false;
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

    private void createDevelopKeystore() throws IOException, InterruptedException {
        Path keystore = Constants.USER_SUBSTRATE_PATH.resolve(Constants.ANDROID_KEYSTORE);
        
        if (Files.exists(keystore)) {
            Logger.logDebug("The " + Constants.ANDROID_KEYSTORE + " file already exists, skipping");
            return;
        }

        int processResult;

        ProcessRunner generateTestKey = new ProcessRunner("keytool", "-genkey", "-v", "-keystore", keystore.toString(), "-storepass",
            "android", "-alias", "androiddebugkey", "-keypass", "android", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=Android Debug,O=Android,C=US", "-noprompt");
        processResult = generateTestKey.runProcess("generateTestKey");
        if (processResult != 0)
            throw new IllegalArgumentException("fatal, can not create a keystore");

        Logger.logDebug("Done creating " + Constants.ANDROID_KEYSTORE);
    }

    private String findLatestBuildTool(Path sdkPath) throws IOException {
        Objects.requireNonNull(sdkPath);
        Path buildToolsPath = sdkPath.resolve("build-tools");
        if (Files.exists(buildToolsPath)) {
            return Files.walk(buildToolsPath, 1)
                    .filter(file -> Files.isDirectory(file) && !file.equals(buildToolsPath))
                    .map(file -> new Version(file.getFileName().toString()))
                    .max(Version::compareTo)
                    .map(Version::toString)
                    .orElseThrow(BuildToolNotFoundException::new);
        }
        throw new BuildToolNotFoundException();
        // TODO: If no build tool is found, we can install it using sdkmanager.
        //  Currently, sdkmanager doesn't work with JDK 11: https://issuetracker.google.com/issues/67495440
    }

    private static class BuildToolNotFoundException extends IOException {
        public BuildToolNotFoundException() {
            super("Android build tools not found. Please install it and try again.");
        }
    }
}
