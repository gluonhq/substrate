/*
 * Copyright (c) 2019, Gluon
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

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
public class ProjectConfiguration {

    private String graalPath;
    private String javaStaticSdkVersion = Constants.DEFAULT_JAVA_STATIC_SDK_VERSION;
    private String javaStaticLibs;
    private String javaFXStaticSDK;

    private String javafxStaticSdkVersion;

    private String llcPath;
    private String StaticRoot;
    private boolean useJNI = true;
    private boolean useJavaFX = false;
    private boolean usePrismSW = false;
    private boolean enableCheckHash = true;
    private boolean verbose = false;

    private Triplet targetTriplet;
    private Triplet hostTriplet;
    private String backend;
    private List<String> bundlesList = Collections.emptyList();
    private List<String> resourcesList = Collections.emptyList();
    private List<String> reflectionList = Collections.emptyList();
    private List<String> jniList;
    private List<String> delayInitList;
    private List<String> runtimeArgsList;
    private List<String> releaseSymbolsList;

    private String appName;
    private String mainClassName;

    private IosSigningConfiguration iosSigningConfiguration = new IosSigningConfiguration();

    public ProjectConfiguration() {}

    public String getGraalPath() {
        return this.graalPath;
    }

    public void setGraalPath(String path) {
        this.graalPath = path;
    }

    /**
     * Returns the version string for the static JDK libs.
     * If this has not been specified before, the default will be
     * returned.
     * @return the specified JavaStaticSDK version, or the default
     */
    public String getJavaStaticSdkVersion() {
        return javaStaticSdkVersion;
    }

    /**
     * Sets the Java static SDK version
     * This is only relevant when no specific custom location
     * for the Java static libs is provided via
     * <code>setJavaStaticLibs</code>
     * If this method is not called, calls to
     * <code>getJavaStaticSdkVersion</code> will return a default value.
     * @param javaStaticSdkVersion the Java static SDK version
     */
    public void setJavaStaticSdkVersion(String javaStaticSdkVersion) {
        this.javaStaticSdkVersion = javaStaticSdkVersion;
    }

    /**
     * Sets the location for the static JDK libs (e.g. libjava.a)
     * When this method is used, subsecquent calls to
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
                .resolve(targetTriplet.getOsArch())
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
                .resolve(targetTriplet.getOsArch())
                .resolve("sdk");
        return answer;
    }

    public Path getJavafxStaticLibsPath() {
        return getJavafxStaticPath().resolve("lib");
    }

    public String getJavafxStaticSdkVersion() {
        return javafxStaticSdkVersion;
    }

    /**
     * Sets the JavaFX static SDK version
     * @param javafxStaticSdkVersion the JavaFX static SDK version
     */
    public void setJavafxStaticSdkVersion(String javafxStaticSdkVersion) {
        this.javafxStaticSdkVersion = javafxStaticSdkVersion;
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

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Enables hash checking to verify integrity of Graal and Java/JavaFX files
     * @param enableCheckHash boolean to enable hash checking
     */
    public void setEnableCheckHash(boolean enableCheckHash) {
        this.enableCheckHash = enableCheckHash;
    }

    public Triplet getTargetTriplet() {
        return targetTriplet;
    }

    /**
     * Sets the target triplet
     * @param targetTriplet the target triplet
     */
    public void setTarget(Triplet targetTriplet) {
        this.targetTriplet = targetTriplet;
    }

    /**
     * Retrieve the host triplet for this configuration.
     * The host triplet is always the triplet for the current runtime, e.g. it should not be set (apart for testing)
     * @return the Triplet for the current executing host
     * @throws IllegalArgumentException in case the current operating system is not supported
     */
    public Triplet getHostTriplet() throws IllegalArgumentException {
        if (hostTriplet == null) {
            hostTriplet = Triplet.fromCurrentOS();
        }
        return hostTriplet;
    }

    public void setHostTriplet(Triplet hostTriplet) {
        this.hostTriplet = hostTriplet;
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
        return bundlesList;
    }

    /**
     * Sets additional bundles
     * @param bundlesList a list of classes that will be added to the default bundlesList list
     */
    public void setBundlesList(List<String> bundlesList) {
        this.bundlesList = bundlesList;
    }

    /**
     * Set additional resources to be included
     * @param resourcesList a list of resource patterns that will be included
     */
    public void setResourcesList(List<String> resourcesList) {
        this.resourcesList = resourcesList;
    }

    public List<String> getResourcesList() {
        return resourcesList;
    }

    public List<String> getReflectionList() {
        return reflectionList;
    }

    /**
     * Sets additional lists
     * @param reflectionList a list of classes that will be added to the default reflection list
     */
    public void setReflectionList(List<String> reflectionList) {
        this.reflectionList = reflectionList;
    }

    public List<String> getJniList() {
        return jniList;
    }

    /**
     * Sets additional lists
     * @param jniList a list of classes that will be added to the default jni list
     */
    public void setJniList(List<String> jniList) {
        this.jniList = jniList;
    }

    public List<String> getDelayInitList() {
        return delayInitList;
    }

    /**
     * Sets additional lists
     * @param delayInitList a list of classes that will be added to the default delayed list
     */
    public void setDelayInitList(List<String> delayInitList) {
        this.delayInitList = delayInitList;
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

    public String getAppName() {
        return appName;
    }

    /**
     * Sets the app name
     * @param appName the name of the application (e.g. demo)
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getMainClassName() {
        return mainClassName;
    }

    /**
     * Sets the FQN of the mainclass (e.g. com.gluonhq.demo.Application)
     * @param mainClassName the FQN of the mainclass
     */
    public void setMainClassName(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    public IosSigningConfiguration getIosSigningConfiguration() {
        return iosSigningConfiguration;
    }

    /**
     * Sets some iOS specific parameters
     * @param iosSigningConfiguration iOS configuration
     */
    public void setIosSigningConfiguration(IosSigningConfiguration iosSigningConfiguration) {
        this.iosSigningConfiguration = iosSigningConfiguration;
    }

    @Override
    public String toString() {
        return "ProjectConfiguration{" +
                "graalPath='" + graalPath + '\'' +
                ", javaStaticSdkVersion='" + javaStaticSdkVersion + '\'' +
                ", javafxStaticSdkVersion='" + javafxStaticSdkVersion + '\'' +
                ", llcPath='" + llcPath + '\'' +
                ", StaticRoot='" + StaticRoot + '\'' +
                ", useJNI=" + useJNI +
                ", useJavaFX=" + useJavaFX +
                ", usePrismSW=" + usePrismSW +
                ", enableCheckHash=" + enableCheckHash +
                ", verbose=" + verbose +
                ", targetTriplet=" + targetTriplet +
                ", hostTriplet=" + hostTriplet +
                ", backend='" + backend + '\'' +
                ", bundlesList=" + bundlesList +
                ", resourcesList=" + resourcesList +
                ", reflectionList=" + reflectionList +
                ", jniList=" + jniList +
                ", delayInitList=" + delayInitList +
                ", runtimeArgsList=" + runtimeArgsList +
                ", releaseSymbolsList=" + releaseSymbolsList +
                ", appName='" + appName + '\'' +
                ", iosConfiguration='" + iosSigningConfiguration + '\'' +
                ", mainClassName='" + mainClassName + '\'' +
                '}';
    }
}
