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
import com.gluonhq.substrate.config.ConfigResolver;
import com.gluonhq.substrate.model.ClassPath;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.ReleaseConfiguration;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.Version;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.gluonhq.substrate.Constants.ANDROID_NATIVE_FOLDER;
import static com.gluonhq.substrate.Constants.ANDROID_PROJECT_NAME;
import static com.gluonhq.substrate.Constants.DALVIK_PRECOMPILED_CLASSES;
import static com.gluonhq.substrate.Constants.META_INF_SUBSTRATE_DALVIK;
import static com.gluonhq.substrate.model.ReleaseConfiguration.DEFAULT_CODE_NAME;
import static com.gluonhq.substrate.model.ReleaseConfiguration.DEFAULT_CODE_VERSION;

public class AndroidTargetConfiguration extends PosixTargetConfiguration {

    private final String ndk;
    private final String sdk;
    private final Path ldlld;
    private final Path clang;
    private final String hostPlatformFolder;

    private List<String> androidAdditionalSourceFiles = Arrays.asList("launcher.c", "javafx_adapter.c",
            "touch_events.c", "glibc_shim.c", "attach_adapter.c", "logger.c");
    private List<String> androidAdditionalHeaderFiles = Arrays.asList("grandroid.h", "grandroid_ext.h");
    private List<String> cFlags = Arrays.asList("-target", "aarch64-linux-android", "-I.");
    private List<String> linkFlags = Arrays.asList("-target", "aarch64-linux-android21", "-fPIC", "-fuse-ld=gold",
            "-Wl,--rosegment,--gc-sections,-z,noexecstack", "-shared",
            "-landroid", "-llog", "-lffi", "-llibchelper");
    private List<String> javafxLinkFlags = Arrays.asList("-Wl,--whole-archive",
            "-lprism_es2_monocle", "-lglass_monocle", "-ljavafx_font_freetype", "-ljavafx_iio", "-Wl,--no-whole-archive",
            "-lGLESv2", "-lEGL", "-lfreetype");
    private final String capLocation = ANDROID_NATIVE_FOLDER + "cap/";

    public AndroidTargetConfiguration( ProcessPaths paths, InternalProjectConfiguration configuration ) throws IOException {
        super(paths,configuration);

        this.sdk = fileDeps.getAndroidSDKPath().toString();
        this.ndk = fileDeps.getAndroidNDKPath().toString();
        this.hostPlatformFolder = configuration.getHostTriplet().getOs() + "-x86_64";

        Path ldguess = Paths.get(this.ndk, "toolchains", "llvm", "prebuilt", hostPlatformFolder, "bin", "ld.lld");
        this.ldlld = Files.exists(ldguess) ? ldguess : null;

        Path clangguess = Paths.get(this.ndk, "toolchains", "llvm", "prebuilt", hostPlatformFolder, "bin", "clang");
        this.clang = Files.exists(clangguess) ? clangguess : null;
        if (projectConfiguration.getGraalVersion().compareTo(new Version("20.1.0")) > 0) {
            projectConfiguration.setBackend(Constants.BACKEND_LIR);
        }
    }

    @Override
    public boolean compile() throws IOException, InterruptedException {
        // we override compile as we need to do some checks first. If we have no ld.lld in android_ndk, we should not start compiling
        if (ndk == null) throw new IOException ("Can't find an Android NDK on your system. Set the environment property ANDROID_NDK");
        if (ldlld == null) throw new IOException ("You specified an android NDK, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/"+hostPlatformFolder+"/bin/ld.lld");
        if (clang == null) throw new IOException ("You specified an android NDK, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/"+hostPlatformFolder+"/bin/clang");

        return super.compile();
    }

    @Override
    public boolean link() throws IOException, InterruptedException {
        // we override link as we need to do some checks first. If we have no clang in android_ndk, we should not start linking
        if (ndk == null) throw new IOException ("Can't find an Android NDK on your system. Set the environment property ANDROID_NDK");
        if (clang == null) throw new IOException ("You specified an android NDK, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/"+hostPlatformFolder+"/bin/clang");
        if (sdk == null) throw new IOException ("Can't find an Android SDK on your system. Set the environment property ANDROID_SDK");

        return super.link();
    }

    @Override
    public boolean packageApp() throws IOException, InterruptedException {
        prepareAndroidProject();
        prepareAndroidManifest();
        prepareAndroidResources();
        copyAarLibraries();
        copyOtherDalvikClasses();
        copySubstrateLibraries();
        String configuration = generateSigningConfiguration();

        fileDeps.checkAndroidPackages(sdk);
        ProcessRunner assembleDebug = new ProcessRunner(
                            getAndroidProjectPath().resolve("gradlew").toString(),
                            "-p", getAndroidProjectPath().toString(),
                            "assemble" + configuration);
        assembleDebug.addToEnv("ANDROID_HOME", sdk);
        assembleDebug.addToEnv("JAVA_HOME", projectConfiguration.getGraalPath().toString());
        if (assembleDebug.runProcess("package-task") != 0) {
            return false;
        }
        Path generatedApk = getAndroidProjectPath().resolve("app").resolve("build")
                            .resolve("outputs").resolve("apk").resolve(configuration.toLowerCase(Locale.ROOT))
                            .resolve("app-"+configuration.toLowerCase(Locale.ROOT)+".apk");
        Path targetApk = paths.getGvmPath().resolve(projectConfiguration.getAppName()+".apk");
        if (Files.exists(generatedApk)) {
            FileOps.copyFile(generatedApk, targetApk);
        }
        return true;
    }

    @Override
    public boolean install() throws IOException, InterruptedException {
        String configuration = generateSigningConfiguration();
        ProcessRunner installDebug = new ProcessRunner(
                            getAndroidProjectPath().resolve("gradlew").toString(),
                            "-p", getAndroidProjectPath().toString(),
                            "install" + configuration);
        installDebug.addToEnv("ANDROID_HOME", sdk);
        installDebug.addToEnv("JAVA_HOME", projectConfiguration.getGraalPath().toString());
        return installDebug.runProcess("install-task") == 0;
    }

    @Override
    public boolean runUntilEnd() throws IOException, InterruptedException {
        Path sdkPath = Paths.get(sdk);
        String adb = sdkPath.resolve("platform-tools").resolve("adb").toString();

        Runnable logcat = () -> {
            try {
                ProcessRunner clearLog = new ProcessRunner(adb,
                        "logcat", "-c");
                clearLog.runProcess("clearLog");

                ProcessRunner log = new ProcessRunner(adb,
                        "logcat", "-v", "brief", "-v", "color",
                        "GraalCompiled:V", "GraalActivity:V",
                        "GraalGluon:V", "GluonAttach:V",
                        "AndroidRuntime:E", "ActivityManager:W", "*:S");
                log.setInfo(true);
                log.runProcess("log");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        };

        Thread logger = new Thread(logcat);
        logger.start();

        ProcessRunner run = new ProcessRunner(adb,
                "shell", "monkey", "-p", getAndroidPackageName(), "1");
        int processResult = run.runProcess("run");
        if (processResult != 0) throw new IOException("Application starting failed!");

        logger.join();
        return processResult == 0;
    }

    @Override
    List<String> getTargetSpecificAOTCompileFlags() throws IOException {
        ArrayList<String> flags = new ArrayList<String>(Arrays.asList(
                "-H:-SpawnIsolates",
                "-Dsvm.targetArch=" + projectConfiguration.getTargetTriplet().getArch(),
                "-H:+UseOnlyWritableBootImageHeap",
                "-H:+UseCAPCache",
                "-H:CAPCacheDir=" + getCapCacheDir().toAbsolutePath().toString(),
                "-H:CompilerBackend=" + projectConfiguration.getBackend()));
        if (projectConfiguration.isUseLLVM()) {
            flags.add("-H:CustomLD=" + ldlld.toAbsolutePath().toString());
        }
        if (projectConfiguration.getGraalVersion().compareTo(new Version("20.1.0")) > 0) {
            flags.add("-H:+UseBionicC");
        }
        return flags;
    }

    @Override
    List<String> getTargetSpecificObjectFiles() throws IOException {
        if (projectConfiguration.isUseLLVM()) {
            return FileOps.findFile(paths.getGvmPath(), "llvm.o").map( objectFile ->
                    Collections.singletonList(objectFile.toAbsolutePath().toString())
            ).orElseThrow();
        }
        else {
            return super.getTargetSpecificObjectFiles();
        }
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
    String getLinkOutputName() {
        String appName = projectConfiguration.getAppName();
        return "lib" + appName + ".so";
    }

    @Override
    protected List<Path> getStaticJDKLibPaths() throws IOException {
        return Arrays.asList(fileDeps.getJavaSDKLibsPath());
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
        return ANDROID_NATIVE_FOLDER + "c/";
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        return androidAdditionalSourceFiles;
    }

    @Override
    List<String> getAdditionalHeaderFiles() {
        return androidAdditionalHeaderFiles;
    }

    private Path getAndroidProjectPath() {
        return paths.getGvmPath().resolve("android_project");
    }

    private Path getAndroidProjectMainPath() {
        return getAndroidProjectPath().resolve("app").resolve("src").resolve("main");
    }

    /**
     * Walks through the jars in the classpath, excluding the JavaFX ones,
     * and looks for META-INF/substrate/dalvik/*.class files.
     *
     * The method will copy all the class files found into jar in the target folder
     *
     * @throws IOException
     */
    private void copyOtherDalvikClasses() throws IOException, InterruptedException {
        Path targetFolder = getAndroidProjectPath().resolve("libs").resolve("tmp");

        if (!Files.exists(targetFolder)) {
            Files.createDirectories(targetFolder);
        }

        Logger.logDebug("Scanning for dalvik classes");
        final List<File> jars = new ClassPath(projectConfiguration.getClasspath()).getJars(true);
        String prefix = META_INF_SUBSTRATE_DALVIK + DALVIK_PRECOMPILED_CLASSES;
        for (File jar : jars) {
            try (ZipFile zip = new ZipFile(jar)) {
                Logger.logDebug("Scanning " + jar);
                for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                    ZipEntry zipEntry = e.nextElement();
                    String name = zipEntry.getName();
                    if (!zipEntry.isDirectory() && name.startsWith(prefix)) {
                        Path classPath = targetFolder.resolve(name.substring(prefix.length()));
                        Logger.logDebug("Adding classes from " + zip.getName() + " :: " + name + " into " + classPath);
                        FileOps.copyStream(zip.getInputStream(zipEntry), classPath);
                    }
                }
            } catch (IOException e) {
                throw new IOException("Error processing dalvik classes from jar: " + jar + ": " + e.getMessage() + ", " + Arrays.toString(e.getSuppressed()));
            }
        }
        ProcessRunner createJar = new ProcessRunner(
                projectConfiguration.getGraalPath().resolve("bin").resolve("jar").toString(),
                "-cvf", "../additional_classes.jar", ".");

        createJar.runProcess("merge-dalvik-classes", targetFolder.toFile());
        FileOps.deleteDirectory(targetFolder);
    }

    /**
     * Copies native libraries to android project
     *
     * @throws IOException
     */
    private void copySubstrateLibraries() throws IOException {
        Path projectLibsLocation = getAndroidProjectMainPath().resolve("jniLibs").resolve("arm64-v8a");

        if (!Files.exists(projectLibsLocation)) {
            Files.createDirectories(projectLibsLocation);
        }

        if (projectConfiguration.isUseJavaFX()) {
            Path javafxFreetypeLibPath = fileDeps.getJavaFXSDKLibsPath().resolve("libfreetype.so");
            Path freetypeLibPath = projectLibsLocation.resolve("libfreetype.so");
            Files.copy(javafxFreetypeLibPath, freetypeLibPath, StandardCopyOption.REPLACE_EXISTING);
        }

        Path libsubstrate = paths.getAppPath().resolve(getLinkOutputName());
        Files.copy(libsubstrate, projectLibsLocation.resolve("libsubstrate.so"), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Generates release signing configuration if
     * keystore is provided, else use debug configuration
     *
     * @return chosen configuration name
     * @throws IOException
     */
    private String generateSigningConfiguration() throws IOException {
        Path settingsFile = getAndroidProjectPath().resolve("app").resolve("keystore.properties");

        ReleaseConfiguration releaseConfiguration = projectConfiguration.getReleaseConfiguration();
        String keyStorePath = releaseConfiguration.getProvidedKeyStorePath();
        String keyStorePass = releaseConfiguration.getProvidedKeyStorePassword();
        String keyAlias = releaseConfiguration.getProvidedKeyAlias();
        String keyPass = releaseConfiguration.getProvidedKeyAliasPassword();
        if (keyStorePath == null ||
                !keyStorePath.endsWith(".keystore") || !Files.exists(Path.of(keyStorePath)) ||
                keyAlias == null || keyStorePass == null || keyPass == null) {
            if (keyStorePath != null) {
                Logger.logSevere("The key store path " + keyStorePath + " is not valid.");
            }
            return "Debug";
        }
        FileOps.replaceInFile(settingsFile, "KEYSTORE_FILE", keyStorePath);
        FileOps.replaceInFile(settingsFile, "KEYSTORE_PASSWORD", keyStorePass);
        FileOps.replaceInFile(settingsFile, "KEY_ALIAS", keyAlias);
        FileOps.replaceInFile(settingsFile, "KEY_PASSWORD", keyPass);
        return "Release";
    }

    /**
     * Copies the .cap files from the jar resource and store them in
     * a directory. Return that directory
     *
     * @throws IOException
     */
    private Path getCapCacheDir() throws IOException {
        Path capPath = paths.getGvmPath().resolve("capcache");
        if (!Files.exists(capPath)) {
            FileOps.copyDirectoryFromResources(capLocation, capPath);
        }
        return capPath;
    }

    /**
     * Copies the Android project from the jar resource and stores it in
     * a directory. Return that directory
     *
     * @return Path of the Android project
     * @throws IOException
     */
    private Path prepareAndroidProject() throws IOException {
        Path androidProject = getAndroidProjectPath();
        if (Files.exists(androidProject)) {
            FileOps.deleteDirectory(androidProject);
        }
        FileOps.copyDirectoryFromResources(ANDROID_NATIVE_FOLDER + ANDROID_PROJECT_NAME, androidProject);
        getAndroidProjectPath().resolve("gradlew").toFile().setExecutable(true);
        return androidProject;
    }

    /**
     * If android manifest is present in src/android, it will be copied to
     * android project.
     *
     * Else, default android manifest is adjusted and used in project
     * configuration.
     *
     * @return the path where android manifest is located
     * @throws IOException
     */
    private Path prepareAndroidManifest() throws IOException {
        String targetOS = projectConfiguration.getTargetTriplet().getOs();
        Path sourcePath = paths.getSourcePath().resolve(targetOS);

        Path userManifest = sourcePath.resolve(Constants.MANIFEST_FILE);
        Path targetManifest = getAndroidProjectMainPath().resolve(Constants.MANIFEST_FILE);
        Path generatedManifest = paths.getGenPath().resolve(targetOS).resolve(Constants.MANIFEST_FILE);

        ReleaseConfiguration releaseConfiguration = projectConfiguration.getReleaseConfiguration();

        if (!Files.exists(userManifest)) {
            // use default manifest
            FileOps.replaceInFile(targetManifest, "package='com.gluonhq.helloandroid'", "package='" + getAndroidPackageName() + "'");
            String newAppLabel = Optional.ofNullable(releaseConfiguration.getAppLabel())
                    .orElse(projectConfiguration.getAppName());
            FileOps.replaceInFile(targetManifest, "A HelloGraal", newAppLabel);
            String newVersionCode = Optional.ofNullable(releaseConfiguration.getVersionCode())
                    .orElse(DEFAULT_CODE_VERSION);
            FileOps.replaceInFile(targetManifest, ":versionCode='1'", ":versionCode='" + newVersionCode + "'");
            String newVersionName = Optional.ofNullable(releaseConfiguration.getVersionName())
                    .orElse(DEFAULT_CODE_NAME);
            FileOps.replaceInFile(targetManifest, ":versionName='1.0'", ":versionName='" + newVersionName + "'");
            FileOps.replaceInFile(targetManifest, "<!-- PERMISSIONS -->", String.join("\n    ", requiredPermissions()));
            FileOps.copyFile(targetManifest, generatedManifest);
            Logger.logInfo("Default Android manifest generated in " + generatedManifest.toString() + ".\n" +
                    "Consider copying it to " + userManifest.toString() + " before performing any modification");
        } else {
            // update manifest in src/android
            String versionCode = FileOps.getNodeValue(userManifest.toString(), "manifest", ":versionCode");
            String newVersionCode = releaseConfiguration.getVersionCode();
            if (versionCode != null && newVersionCode != null) {
                FileOps.replaceInFile(userManifest, ":versionCode='" + versionCode + "'", ":versionCode='" +
                        newVersionCode + "'");
            }
            String versionName = FileOps.getNodeValue(userManifest.toString(), "manifest", ":versionName");
            String newVersionName = releaseConfiguration.getVersionName();
            if (versionName != null && newVersionName != null) {
                FileOps.replaceInFile(userManifest, ":versionName='" + versionName + "'", ":versionName='" +
                        newVersionName + "'");
            }
            String appLabel = FileOps.getNodeValue(userManifest.toString(), "application", ":label");
            String newAppLabel = releaseConfiguration.getAppLabel();
            if (appLabel != null && newAppLabel != null) {
                FileOps.replaceInFile(userManifest, ":label='" + appLabel + "'", ":label='" +
                        newAppLabel + "'");
            }
            Files.copy(userManifest, targetManifest, StandardCopyOption.REPLACE_EXISTING);
        }
        return targetManifest;
    }

    /**
     * If resources are present in src/android, they would
     * be copied to android project.
     *
     * Else, default resources are used
     *
     * @return the path where resources are located
     * @throws IOException
     */
    private Path prepareAndroidResources() throws IOException {
        String targetOS = projectConfiguration.getTargetTriplet().getOs();
        Path targetSourcePath = paths.getSourcePath().resolve(targetOS);

        Path userAssets = targetSourcePath.resolve(Constants.ANDROID_RES_FOLDER);
        Path targetAssets = getAndroidProjectMainPath().resolve("res");
        Path generatedAssets = paths.getGenPath().resolve(targetOS).resolve("res");

        if (!Files.exists(userAssets) || !(Files.isDirectory(userAssets) && Files.list(userAssets).count() > 0)) {
            // Default assets are used
            // Copy template sources to genSrc
            FileOps.copyDirectory(targetAssets, generatedAssets);
            Logger.logInfo("Default Android resources generated in " + generatedAssets.toString() + ".\n" +
                    "Consider copying them to " + userAssets.toString() + " before performing any modification");
            return generatedAssets;
        } else {
            // Copy user assets
            FileOps.copyDirectory(userAssets, targetAssets);
        }
        return targetAssets;
    }

    /**
     * Takes the appId from the project configuration and translates it to an
     * android package name friendly version. It does this by removing all
     * characters that don't match the following characters: a-z, A-Z, 0-9,
     * dot or underscore.
     * @return
     */
    private String getAndroidPackageName() {
        String appId = projectConfiguration.getAppId();
        return appId.replaceAll("[^a-zA-Z0-9\\._]", "");
    }

    /**
     * Scans the classpath for Attach Services
     * and returns a list of permissions in XML tags
     */
    private List<String> requiredPermissions() {
        final ConfigResolver configResolver;
        try {
            configResolver = new ConfigResolver(projectConfiguration.getClasspath());
            final Set<String> androidPermissions = configResolver.getAndroidPermissions();
            return androidPermissions.stream()
                    .map(permission -> "<uses-permission android:name=\"" + permission + "\"/>")
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    /**
     * Scans the classpath for Attach Services
     * and extracts all aar libraries found
     */
    private void copyAarLibraries() throws IOException, InterruptedException {
        Path libPath = getAndroidProjectPath().resolve("libs");
        final List<File> jars = new ClassPath(projectConfiguration.getClasspath()).getJars(true);
        for (File jar : jars) {
            FileOps.extractFilesFromJar(".aar", jar.toPath(), libPath, null);
        }
    }
}
