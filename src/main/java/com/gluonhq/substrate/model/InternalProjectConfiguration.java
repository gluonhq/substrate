/*
 * Copyright (c) 2019, 2020, Gluon
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
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.Strings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    private String llcPath;
    private String staticRoot;
    private boolean useJNI = true;
    private boolean useJavaFX = false;
    private boolean usePrismSW = false;
    private boolean enableCheckHash = true;
    private boolean verbose = false;

    private String backend;
    private List<String> bundlesList = Collections.emptyList();
    private List<String> resourcesList = Collections.emptyList();
    private List<String> reflectionList = Collections.emptyList();
    private List<String> jniList = Collections.emptyList();
    private List<String> initBuildTimeList;
    private List<String> runtimeArgsList;
    private List<String> releaseSymbolsList;

    private IosSigningConfiguration iosSigningConfiguration = new IosSigningConfiguration();

    private ProjectConfiguration publicConfig;

    /**
     * Private projects configuration, which includes everything, including public settings
     * @param config public project configuration
     */
    public InternalProjectConfiguration(ProjectConfiguration config) {

        this.publicConfig = Objects.requireNonNull(config);

        boolean skipCompile = Boolean.parseBoolean(System.getProperty("skipcompile", "false"));
        boolean skipSigning = Boolean.parseBoolean(System.getProperty("skipsigning", "false"));

        setUsePrismSW(config.isUsePrismSW());
        getIosSigningConfiguration().setSkipSigning(skipSigning);

        setJavaStaticLibs(System.getProperty("javalibspath")); // this can be safely set even if null. Default will be used in that case
        setJavaFXStaticSDK(System.getProperty("javafxsdk"));  // this can be safely set even if null. Default will be used in that case
        setInitBuildTimeList(Strings.split(System.getProperty("initbuildtimelist")));
    }

    public Path getGraalPath() {
        return Objects.requireNonNull( this.publicConfig.getGraalPath(), "GraalVM Path is not defined");
    }

    /**
     * Returns the version string for the static JDK libs.
     * If this has not been specified before, the default will be
     * returned.
     * @return the specified JavaStaticSDK version, or the default
     */
    public String getJavaStaticSdkVersion() {
        return Optional.ofNullable(publicConfig.getJavaStaticSdkVersion())
                       .orElse(Constants.DEFAULT_JAVA_STATIC_SDK_VERSION);
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
        return javaStaticLibs != null ? Paths.get(javaStaticLibs) : getDefaultJavaStaticLibsPath();
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
        Path answer = Constants.USER_SUBSTRATE_PATH
                .resolve("javaStaticSdk")
                .resolve(getJavaStaticSdkVersion())
                .resolve(getTargetTriplet().getOsArch())
                .resolve("labs-staticjdk");
        return answer;
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
        return javaFXStaticSDK != null? Paths.get(javaFXStaticSDK): getDefaultJavafxStaticPath();

    }

     Path getDefaultJavafxStaticPath() {
            Path answer = Constants.USER_SUBSTRATE_PATH
                .resolve("javafxStaticSdk")
                .resolve(getJavafxStaticSdkVersion())
                .resolve(getTargetTriplet().getOsArch())
                .resolve("sdk");
        return answer;
    }

    public Path getJavafxStaticLibsPath() {
        return getJavafxStaticPath().resolve("lib");
    }

    public String getJavafxStaticSdkVersion() {
        return Optional.ofNullable(publicConfig.getJavafxStaticSdkVersion())
                       .orElse(Constants.DEFAULT_JAVAFX_STATIC_SDK_VERSION);
    }

    public String getLlcPath() {
        return llcPath;
    }

    /**
     * Sets the LLC directory by the user
     * @param llcPath the directory (e.g "$user/Downloads/llclib") that contains LLC
     */
    public void setLlcPath(String llcPath) {
        this.llcPath = llcPath;
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
                : getAndroidSdkPath().resolve("ndk-bundle");
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

    /**
     * Enables hash checking to verify integrity of Graal and Java/JavaFX files
     * @param enableCheckHash boolean to enable hash checking
     */
    public void setEnableCheckHash(boolean enableCheckHash) {
        this.enableCheckHash = enableCheckHash;
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

    public boolean isUseLLVM() {
        return "llvm".equals(backend);
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
        return runtimeArgsList;
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
     * Sets additional lists
     * @param runtimeArgsList a list of classes that will be added to the default runtime args list
     */
    public void setRuntimeArgsList(List<String> runtimeArgsList) {
        this.runtimeArgsList = runtimeArgsList;
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

    public IosSigningConfiguration getIosSigningConfiguration() {
        return Optional.ofNullable(publicConfig.getIosSigningConfiguration()).orElse(new IosSigningConfiguration());
    }

    /**
     * check if the GraalVM provided by the configuration is capable of running native-image
     * @throws NullPointerException when the configuration is null
     * @throws IllegalArgumentException when the configuration doesn't contain a property graalPath
     * @throws IOException when the path to bin/native-image doesn't exist
     */
    public void canRunNativeImage() throws IOException, InterruptedException {
        Path javaCmd =  getGraalVMBinPath().resolve("java");
        ProcessRunner processRunner = new ProcessRunner(javaCmd.toString(), "-version");
        if (processRunner.runProcess("check version") != 0) {
            throw new IllegalArgumentException("$GRAALVM_HOME/bin/java -version process failed");
        }
        for (String l : processRunner.getResponses()) {
            if (l == null || l.isEmpty()) {
                throw new IllegalArgumentException(javaCmd + " -version failed to return a valid value for GraalVM");
            }
            if (l.indexOf("1.8") > 0) {
                throw new IllegalArgumentException("You are using an old version of GraalVM in " + javaCmd +
                        " which uses Java version " + l + "\nUse GraalVM 19.3 or later");
            }
        }
    }

    /**
     * Gets $GRAALVM/bin path or throws an IOException if the path is not found
     * It also verifies that native-image is installed in that path.
     *
     * @return the path to $GRAALVM/bin
     * @throws IOException If $GRAALVM, $GRAALVM/bin or $GRAALVM/bin/native-image paths
     *                    don't exist
     */
    private Path getGraalVMBinPath() throws IOException {
        Path graalPath = getGraalPath();//Path.of(graalPathString);
        if (!Files.exists(graalPath)) throw new IOException("Path provided for GraalVM doesn't exist: " + graalPath);
        Path binPath = graalPath.resolve("bin");
        if (!Files.exists(binPath)) throw new IOException("Path provided for GraalVM doesn't contain a bin directory: " + graalPath);
        Path niPath = Constants.OS_WINDOWS.equals(getHostTriplet().getOs()) ?
                binPath.resolve("native-image.cmd") :
                binPath.resolve("native-image");
        if (!Files.exists(niPath)) throw new IOException("Path provided for GraalVM doesn't contain bin/native-image: " + graalPath + "\n" +
                "You can use gu to install it running: \n${GRAALVM_HOME}/bin/gu install native-image");
        return binPath;
    }

    @Override
    public String toString() {
        return "ProjectConfiguration{" +
                "graalPath='" + publicConfig.getGraalPath() + '\'' +
                ", javaStaticSdkVersion='" + getJavaStaticSdkVersion() + '\'' +
                ", javafxStaticSdkVersion='" + getJavafxStaticSdkVersion() + '\'' +
                ", llcPath='" + llcPath + '\'' +
                ", StaticRoot='" + staticRoot + '\'' +
                ", useJNI=" + useJNI +
                ", useJavaFX=" + useJavaFX +
                ", usePrismSW=" + usePrismSW +
                ", enableCheckHash=" + enableCheckHash +
                ", verbose=" + verbose +
                ", targetTriplet=" + getTargetTriplet() +
                ", hostTriplet=" + getHostTriplet() +
                ", backend='" + backend + '\'' +
                ", bundlesList=" + bundlesList +
                ", resourcesList=" + resourcesList +
                ", reflectionList=" + reflectionList +
                ", jniList=" + jniList +
                ", initBuildTimeList=" + initBuildTimeList +
                ", runtimeArgsList=" + runtimeArgsList +
                ", releaseSymbolsList=" + releaseSymbolsList +
                ", appName='" + getAppName() + '\'' +
                ", iosConfiguration='" + iosSigningConfiguration + '\'' +
                ", mainClassName='" + getMainClassName() + '\'' +
                '}';
    }
}
