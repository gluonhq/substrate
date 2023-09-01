/*
 * Copyright (c) 2019, 2023, Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
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
package com.gluonhq.substrate.model;

import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.ProjectConfiguration;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.Strings;
import com.gluonhq.substrate.util.Version;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.gluonhq.substrate.target.AndroidTargetConfiguration.ANDROID_NDK_VERSION;

/**
 * This class contains all configuration info about the current project (not about the current OS/Arch/vendor etc)
 *
 * This class allows to specify where the Java core libs are located. If <code>setJavaStaticLibs</code> is called,
 * the libraries are expected to be found in the location provided by the location passed to
 * <code>setJavaStaticLibs</code>
 *
 * If this method has not been called, getJavaStaticLibsPath() will return the default location, taking into account
 * the value of javaStaticSdkVersion. If that value is not set, the default value is used.
 */
public class InternalProjectConfiguration {

    private String javaStaticLibs;
    private String javaFXStaticSDK;

    private boolean useJNI = true;
    private boolean useJavaFX = false;
    private boolean usePrismSW = false;
    private boolean enableCheckHash = true;
    private boolean usesJDK11 = false;
    private boolean sharedLibrary = false;
    private boolean staticLibrary = false;

    private String backend;
    private List<String> initBuildTimeList;
    private List<String> releaseSymbolsList;

    private final ProjectConfiguration publicConfig;

    /**
     * Private projects configuration, which includes everything, including public settings
     * @param config public project configuration
     */
    public InternalProjectConfiguration(ProjectConfiguration config) {

        this.publicConfig = Objects.requireNonNull(config);

        setUsePrismSW(config.isUsePrismSW());
        if (Boolean.getBoolean("skipsigning")) {
            // override value from client plugin when system property is set:
            getReleaseConfiguration().setSkipSigning(true);
        }
        setJavaStaticLibs(System.getProperty("javalibspath")); // this can be safely set even if null. Default will be used in that case
        String javafxStaticSdkPath = System.getenv("JAVAFX_STATIC_SDK_PATH");
        if (javafxStaticSdkPath != null) {
            Path lib = Path.of(javafxStaticSdkPath, "lib");
            if (!Files.exists(lib) || !Files.isDirectory(lib)) {
                throw new IllegalArgumentException("Error: " + lib + " is not a valid SDK path.\n" +
                        "Make sure you provide a valid JAVAFX_STATIC_SDK_PATH or unset it");
            }
            if (!Files.exists(lib.resolve("javafx.base.jar")) ||
                    !Files.exists(lib.resolve("javafx.graphics.jar"))) {
                throw new IllegalArgumentException("Error: " + lib + " doesn't contain required JavaFX jars.\n" +
                        "Make sure you provide a valid JAVAFX_STATIC_SDK_PATH or unset it");
            }
        }
        setJavaFXStaticSDK(javafxStaticSdkPath);  // this can be safely set even if null. Default will be used in that case
        setInitBuildTimeList(Strings.split(System.getProperty("initbuildtimelist")));

        boolean useJavaFX = new ClassPath(config.getClasspath()).contains(s -> s.contains("javafx"));
        setUseJavaFX(useJavaFX);

        String graalvmBackend = System.getenv("GRAALVM_COMPILER_BACKEND");
        if (graalvmBackend != null) {
            setBackend(graalvmBackend);
        }

        performHostChecks();
    }

    public Path getGraalPath() {
        return Objects.requireNonNull(this.publicConfig.getGraalPath(), "GraalVM Path is not defined");
    }

    public Version getGraalVersion() throws IOException {
        String pattern = "GraalVM .*?(\\d\\d.\\d.\\d)";
        ProcessRunner graalJava;
        try {
            graalJava = new ProcessRunner(
                            getGraalVMBinPath().resolve("java").toString(),
                            "--version");
            graalJava.runProcess("java-version");
        } catch (InterruptedException e) {
            throw new IOException("Couldn't determine GraalVM version, " + e.toString());
        }
        String output = graalJava.getResponse();
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(output);
        if (!m.find())
            throw new IOException("Couldn't determine GraalVM version");
        return new Version(m.group(1));
    }

    /**
     * Returns the version string for the static JDK libs.
     * If this has not been specified before, the default will be
     * returned.
     * @return the specified JavaStaticSDK version, or the default
     */
    public String getJavaStaticSdkVersion() {
        return Optional.ofNullable(publicConfig.getJavaStaticSdkVersion())
                .orElse(usesJDK11() ?
                        Constants.DEFAULT_JAVA_STATIC_SDK_VERSION11 :
                        Constants.DEFAULT_JAVA_STATIC_SDK_VERSION);
    }

    /**
     * Sets the location for the static JDK libs (e.g. libjava.a)
     * When this method is used, subsequent calls to
     * <code>getStaticLibsPath</code> will override the default
     * location
     * @param location the location of the directory where
     *                 the static libs are expected.
     */
    public void setJavaStaticLibs(String location) {
        this.javaStaticLibs = location;
    }

    /**
     * Returns the Path containing the location of the
     * static libraries. If the <code>setJavaStaticLibs</code>
     * method has been called before, the Path pointed to
     * by the argument to <code>setJavaStaticLibs</code> will be returned.
     * Otherwise, the default location of the static libs will be returned.
     * There is no guarantee that the libraries in the returned directory actually exist.
     * @return the path to the location where the static JDK libraries are expected.
     */
    public Path getJavaStaticLibsPath() {
        return useCustomJavaStaticLibs() ? Paths.get(javaStaticLibs) : getDefaultJavaStaticLibsPath();
    }

    /**
     * Check whether a custom path to static Java libs is
     * provided
     * @return true if a custom path is provided, false otherwise.
     */
    public boolean useCustomJavaStaticLibs() {
        return this.javaStaticLibs != null;
    }

    /**
     * Return the default path where the static JDK is installed for the os-arch combination of this configuration, and for
     * the version in <code>javaStaticSdkVersion</code>
     * @return the path to the Java SDK (including at least the libs)
     */
    public Path getDefaultJavaStaticPath() {
        return Constants.USER_SUBSTRATE_PATH
                .resolve("javaStaticSdk")
                .resolve(getJavaStaticSdkVersion())
                .resolve(getTargetTriplet().getOsArch())
                .resolve(usesJDK11() ? Constants.DEFAULT_JAVASDK_PATH11 : Constants.DEFAULT_JAVASDK_PATH);
    }

    private Path getDefaultJavaStaticLibsPath() {
        return getDefaultJavaStaticPath().resolve("lib").resolve("static");
    }

    /**
     * Sets the location for the JavaFX static SDK
     * At this moment, the JavaFX static SDK contains
     * platform-specific jars and platform-specific static native libraries.
     * When this method is used, subsequent calls to
     * <code>getJavaFXStaticLibsPath</code> and <code>getJavaFXStaticPath</code> will override the default
     * location
     * @param location the location of the directory where
     *                 the JavaFX static SDK expected.
     */
    public void setJavaFXStaticSDK(String location) {
        this.javaFXStaticSDK = location;
    }

    /**
     * Return the path where the static JavaFX SDK is installed for the os-arch combination of this configuration, and for
     * the version in <code>javafxStaticSdkVersion</code>.
     * If the location of the JavaFX SDK has previously been set using
     * <code>setJavaFXStaticSDK</code>, that SDK will be used.
     * @return the path to the JavaFX SDK
     */
    public Path getJavafxStaticPath() {
        return useCustomJavafxStaticLibs() ? Paths.get(javaFXStaticSDK) : getDefaultJavafxStaticPath();
    }

    /**
     * Check whether a custom path to static JavaFX libs is provided.
     *
     * @return true if a custom path is provided, false otherwise.
     */
    public boolean useCustomJavafxStaticLibs() {
        return this.javaFXStaticSDK != null;
    }

    public Path getDefaultJavafxStaticPath() {
        Triplet targetTriplet = getTargetTriplet();
        if (new Triplet(Constants.Profile.IOS_SIM).equals(targetTriplet)) {
            targetTriplet = new Triplet(Constants.Profile.IOS);
        }
        return Constants.USER_SUBSTRATE_PATH
                .resolve("javafxStaticSdk")
                .resolve(getJavafxStaticSdkVersion())
                .resolve(targetTriplet.getOsArch())
                .resolve("sdk");
    }

    public Path getJavafxStaticLibsPath() {
        return getJavafxStaticPath().resolve("lib");
    }

    public String getJavafxStaticSdkVersion() {
        return Optional.ofNullable(publicConfig.getJavafxStaticSdkVersion())
                       .orElse(Constants.DEFAULT_JAVAFX_STATIC_SDK_VERSION);
    }

    /**
     * Gets Android SDK path
     */
    public Path getAndroidSdkPath() {
        String sdkEnv = System.getenv("ANDROID_SDK");
        return (sdkEnv != null) ? Paths.get(sdkEnv)
                : Constants.USER_SUBSTRATE_PATH.resolve("Android");
    }

    /**
     * Gets Android NDK path
     */
    public Path getAndroidNdkPath() {
        String ndkEnv = System.getenv("ANDROID_NDK");
        return (ndkEnv != null) ? Paths.get(ndkEnv)
                : getAndroidSdkPath().resolve("ndk").resolve(ANDROID_NDK_VERSION);
    }

    /**
     * Gets sysroot path for the current arch
     */
    public Path getSysrootPath() {
        String sysrootEnv = System.getenv("SYSROOT");
        return (sysrootEnv != null) ? Paths.get(sysrootEnv)
                : Constants.USER_SUBSTRATE_PATH.resolve("sysroot").resolve(getTargetTriplet().getArch());
    }

    public boolean isUseJNI() {
        return useJNI;
    }

    public void setUseJNI(boolean useJNI) {
        this.useJNI = useJNI;
    }

    public boolean isUseJavaFX() {
        return useJavaFX;
    }

    public void setUseJavaFX(boolean useJavaFX) {
        this.useJavaFX = useJavaFX;
    }

    public boolean isUsePrismSW() {
        return usePrismSW;
    }

    public void setUsePrismSW(boolean usePrismSW) {
        this.usePrismSW = usePrismSW;
    }

    public boolean isEnableCheckHash() {
        return enableCheckHash;
    }

    public boolean isVerbose() {
        return publicConfig.isVerbose();
    }

    public boolean isUsePrecompiledCode() {
        return publicConfig.isUsePrecompiledCode();
    }

    /**
     * Enables hash checking to verify integrity of Graal and Java/JavaFX files
     * @param enableCheckHash boolean to enable hash checking
     */
    public void setEnableCheckHash(boolean enableCheckHash) {
        this.enableCheckHash = enableCheckHash;
    }

    public boolean isSharedLibrary() {
        return sharedLibrary;
    }

    public void setSharedLibrary(boolean sharedLibrary) {
        this.sharedLibrary = sharedLibrary;
    }

    public boolean isStaticLibrary() {
        return staticLibrary;
    }

    public void setStaticLibrary(boolean staticLibrary) {
        this.staticLibrary = staticLibrary;
    }

    public Triplet getTargetTriplet() {
        return Objects.requireNonNull( publicConfig.getTargetTriplet(), "Target triplet is required");
    }


    /**
     * Retrieve the host triplet for this configuration.
     * The host triplet is always the triplet for the current runtime, e.g. it should not be set (apart for testing)
     * @return the Triplet for the current executing host
     * @throws IllegalArgumentException in case the current operating system is not supported
     */
    public Triplet getHostTriplet() throws IllegalArgumentException {
        return Optional.ofNullable(publicConfig.getHostTriplet())
                       .orElse(Triplet.fromCurrentOS());
    }


    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    @Deprecated
    public boolean isUseLLVM() {
        return Constants.BACKEND_LLVM.equals(backend);
    }

    public List<String> getBundlesList() {
        return Optional.ofNullable(publicConfig.getBundlesList())
                       .orElse(Collections.emptyList());
    }

    public List<String> getResourcesList() {
        return Optional.ofNullable(publicConfig.getResourcesList())
                .orElse(Collections.emptyList());
    }

    public List<String> getReflectionList() {
        return Optional.ofNullable(publicConfig.getReflectionList())
                .orElse(Collections.emptyList());
    }

    public List<String> getJniList() {
        return Optional.ofNullable(publicConfig.getJniList())
                .orElse(Collections.emptyList());
    }

    public List<String> getCompilerArgs() {
        return Optional.ofNullable(publicConfig.getCompilerArgs())
                .orElse(Collections.emptyList());
    }

    public List<String> getLinkerArgs() {
        return Optional.ofNullable(publicConfig.getLinkerArgs())
                .orElse(Collections.emptyList());
    }

    public List<String> getInitBuildTimeList() {
        return initBuildTimeList;
    }

    /**
     * Sets additional lists
     * @param initBuildTimeList a list of classes that will be added to the default
     *                          initialize build time list
     */
    public void setInitBuildTimeList(List<String> initBuildTimeList) {
        this.initBuildTimeList = initBuildTimeList;
    }

    public List<String> getRuntimeArgsList() {
        return publicConfig.getRuntimeArgs();
    }

    /**
     * Sets additional lists of release symbols, like _Java_com_gluonhq*
     * @param releaseSymbolsList a list of classes that will be added to the default release symbols list
     */
    public void setReleaseSymbolsList(List<String> releaseSymbolsList) {
        this.releaseSymbolsList = releaseSymbolsList;
    }

    public List<String> getReleaseSymbolsList() {
        return releaseSymbolsList;
    }

    /**
     * Returns the appId from {@link ProjectConfiguration}. If the appId is null, a concatenation of strings
     * "com.gluonhq." and a random string with 6 alphabets, is returned.
     * @return appId from {@link ProjectConfiguration}. If the appId is null, it will return a random string.
     */
    public String getAppId() {
        return Optional.ofNullable(publicConfig.getAppId()).orElse("com.gluonhq." + Strings.randomString(6));
    }

    public String getAppName() {
        return Objects.requireNonNull(publicConfig.getAppName(), "App name is required");
    }

    public String getMainClassName() {
        return publicConfig.getMainClassName();
    }

    public String getClasspath() {
        return publicConfig.getClasspath();
    }

    public boolean hasWeb() {
        return getClasspath().contains("javafx-web") || getClasspath().contains("javafx.web");
    }

    public String getRemoteHostName() { return publicConfig.getRemoteHostName(); }

    public String getRemoteDir() { return publicConfig.getRemoteDir(); }

    public ReleaseConfiguration getReleaseConfiguration() {
        return Optional.ofNullable(publicConfig.getReleaseConfiguration()).orElse(new ReleaseConfiguration());
    }

    public boolean usesJDK11() {
        return usesJDK11;
    }

    /**
     * Check if the GraalVM provided by the configuration is supported
     * @throws IOException if the GraalVM version is older than the minimum supported version
     */
    public void checkGraalVMVersion(Version javaVersion) throws IOException {
        if (isOldGraalVMVersioningScheme(javaVersion)) {
            Version graalVersion = getGraalVersion();
            if (graalVersion.compareTo(new Version(Constants.GRAALVM_MIN_VERSION)) < 0) {
                throw new IOException("Current GraalVM version (" + graalVersion + ") not supported.\n" +
                        "Please upgrade to " + Constants.GRAALVM_MIN_VERSION + " or higher");
            } else {
                // TODO: check the GraalVM version whenever Constants.GRAALVM_MIN_VERSION is bumped and uses the new versioning system
            }
        }
    }

    public boolean isOldGraalVMVersioningScheme(Version javaVersion) {
        if (javaVersion.getMajor() == 17) {
            // JDK 17 versions before 17.0.7 used the old versioning scheme
            return javaVersion.compareTo(new Version(17, 0, 7)) < 0;
        }
        // all other releases before JDK 20 use the old versioning
        // staring from JDK 20, all releases follow the new versioning
        return javaVersion.getMajor() < 20;
    }

    /**
     * Check if the GraalVM's java provided by the configuration is supported
     * @throws IOException if the GraalVM's java version is older than the minimum supported version
     */
    public Version checkGraalVMJavaVersion() throws IOException {
        Version javaVersion = getGraalVMJavaVersion();
        if (javaVersion.compareTo(new Version(Constants.GRAALVM_JAVA_MIN_VERSION)) < 0) {
            throw new IOException("Current GraalVM's java version (" + javaVersion + ") not supported.\n" +
                    "Please upgrade to " + Constants.GRAALVM_JAVA_MIN_VERSION + " or higher");
        }
        usesJDK11 = javaVersion.getMajor() == 11;
        return javaVersion;
    }

    /**
     * Check if Gluon is the vendor of the GraalVM build, or else logs a message.
     * @throws IOException if the GraalVM path or the GraalVM/release file don't exist
     */
    public void checkGraalVMVendor() throws IOException {
        Path graalPath = getGraalPath();
        if (!Files.exists(graalPath)) {
            throw new IOException("Path provided for GraalVM doesn't exist: " + graalPath);
        }
        Path release = graalPath.resolve("release");
        if (!Files.exists(graalPath)) {
            throw new IOException("Path provided for GraalVM/release doesn't exist: " + release);
        }
        if (Files.readAllLines(release).stream().noneMatch(line -> "VENDOR=Gluon".equals(line.trim()))) {
            Logger.logInfo("Substrate is tested with the Gluon's GraalVM build which you can find at https://github.com/gluonhq/graal/releases." +
                    "\nWhile you can still use other GraalVM builds, there is no guarantee that these will work properly with Substrate");
        }
    }

    /**
     * for Android and iOS profiles, verifies that the LLVM toolchain is installed,
     * or installs it otherwise.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Deprecated
    public void canRunLLVM(Triplet triplet) throws IOException, InterruptedException {
        if (!new Triplet(Constants.Profile.IOS).equals(triplet) &&
                !new Triplet(Constants.Profile.ANDROID).equals(triplet) &&
                !new Triplet(Constants.Profile.LINUX_AARCH64).equals(triplet)) {
            // host doesn't use LLVM
            return;
        }
        if (new Triplet(Constants.Profile.IOS).equals(triplet) && !isUseLLVM()) {
            // iOS can use other backends
            return;
        }
        if (new Triplet(Constants.Profile.ANDROID).equals(triplet) && !isUseLLVM()) {
            // Android can use other backends
            return;
        }
        if (new Triplet(Constants.Profile.LINUX_AARCH64).equals(triplet) && !isUseLLVM()) {
            // LINUX_AARCH64 can use other backends
            return;
        }
        Path graalPath = getGraalPath();
        if (!Files.exists(graalPath)) {
            throw new IOException("Path provided for GraalVM doesn't exist: " + graalPath);
        }
        Path llvmPath = graalPath.resolve("lib").resolve("llvm");
        if (!Files.exists(llvmPath) || !Files.exists(llvmPath.resolve("bin"))
                || !Files.exists(llvmPath.resolve("bin").resolve("llvm-config"))) {
            ProcessRunner runner = new ProcessRunner(graalPath.resolve("bin").resolve("gu").toString(), "--jvm", "install", "llvm-toolchain");
            int result = runner.runProcess("install llvm-toolchain");
            if (result != 0) {
                throw new IOException("Error installing llvm-toolchain at: " + graalPath + "\n" +
                        "Please, use gu to install llvm running the following command: \n" +
                        "$GRAALVM_HOME/bin/gu --jvm install llvm-toolchain");
            }
        }
    }

    /**
     * Run any necessary checks required on the host
     */
    private void performHostChecks() {
        if (publicConfig.getGraalPath() == null) {
            return;
        }
        if (Triplet.isMacOSHost()) {
            checkGraalVMPermissions(getGraalPath().toString());
        }
    }

    /**
     * Gets $GRAALVM/bin path or throws an IOException if the path is not found
     * It also verifies that native-image is installed in that path, and
     * installs it otherwise.
     *
     * @return the path to $GRAALVM/bin
     * @throws IOException If $GRAALVM, $GRAALVM/bin or $GRAALVM/bin/native-image paths
     *                    don't exist
     */
    private Path getGraalVMBinPath() throws IOException, InterruptedException {
        Path graalPath = getGraalPath();
        if (!Files.exists(graalPath)) {
            throw new IOException("Path provided for GraalVM doesn't exist: " + graalPath);
        }
        Path binPath = graalPath.resolve("bin");
        if (!Files.exists(binPath)) {
            throw new IOException("Path provided for GraalVM doesn't contain a bin directory: " + graalPath);
        }
        boolean isWindows = Constants.OS_WINDOWS.equals(getHostTriplet().getOs());
        Path niPath = isWindows ? binPath.resolve("native-image.cmd") : binPath.resolve("native-image");
        if (!Files.exists(niPath)) {
            Path guPath = isWindows ? binPath.resolve("gu.cmd") : binPath.resolve("gu");
            ProcessRunner runner = new ProcessRunner(guPath.toString(), "--jvm", "install", "native-image");
            int result = runner.runProcess("install native-image");
            if (result != 0) {
                throw new IOException("Error installing native-image at: " + graalPath + "\n" +
                        "Please, use gu to install native-image running the following command: \n" +
                        (isWindows ? "%GRAALVM_HOME%\\bin\\gu.cmd install native-image" :
                                "$GRAALVM_HOME/bin/gu --jvm install native-image"));
            }
        }
        return binPath;
    }

    /**
     * Prevent undesired dialogs and build failures when running on MacOS 1.15.0+.
     *
     * By default, the OS prevents the access to any non-notarized executable if it
     * has been downloaded.
     *
     * This method will check if the GraalVM folder is under quarantine, and if that
     * is the case, it will remove it recursively from all the files, without the
     * need of deactivating GateKeeper.
     *
     * See https://github.com/oracle/graal/issues/1724
     *
     * @param graalvmHome the path to GraalVM
     */
    private void checkGraalVMPermissions(String graalvmHome) {
        if (graalvmHome == null || graalvmHome.isEmpty()) {
            return;
        }
        String graalvmRoot = graalvmHome.endsWith("/Contents/Home") ?
                Path.of(graalvmHome).getParent().getParent().toString() :
                graalvmHome;
        Logger.logDebug("Checking execution permissions for " + graalvmRoot);
        try {
            ProcessRunner xattrRunner = new ProcessRunner("xattr", graalvmRoot);
            xattrRunner.runProcess("check xattr");
            if (xattrRunner.getResponses().stream().anyMatch("com.apple.quarantine"::equals)) {
                Logger.logInfo("Removing quarantine attributes from GraalVM files at " + graalvmRoot +
                        ".\nYou might be prompted for admin rights.");
                ProcessRunner runner = new ProcessRunner("sudo", "xattr", "-r", "-d",
                        "com.apple.quarantine", graalvmRoot);
                runner.setInteractive(true);
                boolean result = runner.runTimedProcess("remove quarantine", 60L);
                if (result) {
                    Logger.logInfo("Quarantine attributes removed successfully");
                }
            }
        } catch (IOException | InterruptedException e) {
            Logger.logFatal(e,"Error checking execution permissions for " + graalvmRoot);
        }
    }

    /**
     * Gets the Java version that GraalVM bundles
     * @return the Java version of the GraalVM's build
     * @throws NullPointerException when the configuration is null
     * @throws IllegalArgumentException when the configuration doesn't contain a property graalPath
     * @throws IOException if the Java version can't be found
     */
    public Version getGraalVMJavaVersion() throws IOException {
        ProcessRunner graalJava = null;
        try {
            graalJava = new ProcessRunner(getGraalVMBinPath().resolve("java").toString(), "-version");
            if (graalJava.runProcess("check version") != 0) {
                throw new IllegalArgumentException("$GRAALVM_HOME/bin/java -version process failed");
            }
        } catch (InterruptedException e) {
            throw new IllegalArgumentException("$GRAALVM_HOME/bin/java -version process failed");
        }
        String pattern = "version \"(\\d{1,2}(\\.\\d+){0,2})\"";
        Pattern r = Pattern.compile(pattern);
        List<String> responses = graalJava.getResponses();
        if (responses == null || responses.isEmpty()) {
            throw new IOException("Couldn't determine GraalVM's Java version");
        }
        String realVersion = responses.get(0).replaceAll("-internal", "");
        Matcher m = r.matcher(realVersion);
        if (!m.find()) {
            throw new IOException("Couldn't determine GraalVM's Java version for " + responses.get(0));
        }
        return new Version(m.group(1));
    }

    @Override
    public String toString() {
        return "ProjectConfiguration{" +
                "graalPath='" + publicConfig.getGraalPath() + '\'' +
                ", javaStaticSdkVersion='" + getJavaStaticSdkVersion() + '\'' +
                ", javafxStaticSdkVersion='" + getJavafxStaticSdkVersion() + '\'' +
                ", useJNI=" + useJNI +
                ", useJavaFX=" + useJavaFX +
                ", usePrismSW=" + usePrismSW +
                ", enableCheckHash=" + enableCheckHash +
                ", targetTriplet=" + getTargetTriplet() +
                ", hostTriplet=" + getHostTriplet() +
                ", backend='" + backend + '\'' +
                ", bundlesList=" + getBundlesList() +
                ", resourcesList=" + getResourcesList() +
                ", reflectionList=" + getReflectionList() +
                ", jniList=" + getJniList() +
                ", initBuildTimeList=" + getInitBuildTimeList() +
                ", runtimeArgsList=" + getRuntimeArgsList() +
                ", releaseSymbolsList=" + releaseSymbolsList +
                ", appName='" + getAppName() + '\'' +
                ", releaseConfiguration='" + getReleaseConfiguration() + '\'' +
                ", mainClassName='" + getMainClassName() + '\'' +
                ", classpath='" + getClasspath() + '\'' +
                '}';
    }
}
