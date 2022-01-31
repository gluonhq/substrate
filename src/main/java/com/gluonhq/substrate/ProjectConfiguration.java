/*
 * Copyright (c) 2019, 2021, Gluon
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
package com.gluonhq.substrate;

import com.gluonhq.substrate.model.ReleaseConfiguration;
import com.gluonhq.substrate.model.Triplet;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * This class contains all public configuration info about the current project (not about the current OS/Arch/vendor etc)
 * Instances of this class give hints to where real values should be contained from.
 */
public class ProjectConfiguration {

    private Path graalPath;

    private String javafxStaticSdkVersion;
    private String javaStaticSdkVersion;

    private boolean usePrismSW = false;
    private boolean verbose = false;
    private boolean usePrecompiledCode = true;

    private Triplet targetTriplet;
    private Triplet hostTriplet = Triplet.fromCurrentOS();

    private List<String> bundlesList = Collections.emptyList();
    private List<String> resourcesList = Collections.emptyList();
    private List<String> reflectionList = Collections.emptyList();
    private List<String> jniList = Collections.emptyList();
    private List<String> compilerArgs = Collections.emptyList();
    private List<String> linkerArgs = Collections.emptyList();
    private List<String> runtimeArgs = Collections.emptyList();

    private String appId;
    private String appName;
    private final String mainClassName;
    private final String classpath;

    private String remoteHostName;
    private String remoteDir;

    private ReleaseConfiguration releaseConfiguration = new ReleaseConfiguration();

    /**
     * Create a new project configuration.
     *
     * @param mainClassName the fully qualified class name that contains the main entry point
     * @param classpath the class path that is needed to compile the application
     */
    public ProjectConfiguration(String mainClassName, String classpath) {
        this.mainClassName = Objects.requireNonNull(mainClassName, "Main class name is required")
                               .contains("/") ?
                                  mainClassName.substring(mainClassName.indexOf("/") + 1) : mainClassName;
        this.classpath = Objects.requireNonNull(classpath, "Classpath is required");
    }

    public Path getGraalPath() {
        return this.graalPath;
    }

    /**
     * Sets the path to the GraalVM installation folder.
     *
     * @param path the path to the GraalVM installation folder
     */
    public void setGraalPath(Path path) {
        this.graalPath = path;
    }

    /**
     * Sets the Java static SDK version
     * @param javaStaticSdkVersion the Java static SDK version
     */
    public void setJavaStaticSdkVersion(String javaStaticSdkVersion) {
        this.javaStaticSdkVersion = javaStaticSdkVersion;
    }

    public String getJavaStaticSdkVersion() {
        return this.javaStaticSdkVersion;
    }

    /**
     * Sets the JavaFX static SDK version
     * @param javafxStaticSdkVersion the JavaFX static SDK version
     */
    public void setJavafxStaticSdkVersion(String javafxStaticSdkVersion) {
        this.javafxStaticSdkVersion = javafxStaticSdkVersion;
    }

    public String getJavafxStaticSdkVersion() {
        return this.javafxStaticSdkVersion;
    }

    public boolean isUsePrismSW() {
        return usePrismSW;
    }

    public void setUsePrismSW(boolean usePrismSW) {
        this.usePrismSW = usePrismSW;
    }

    /**
     * Specify whether verbose output should be enabled. Passing a value of <code>true</code>
     * will enable verbose output.
     *
     * @param verbose <code>true</code> to enable verbose output
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setUsePrecompiledCode(boolean usePrecompiledCode) {
        this.usePrecompiledCode = usePrecompiledCode;
    }

    public boolean isUsePrecompiledCode() {
        return usePrecompiledCode;
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
        return hostTriplet;
    }

    public void setHostTriplet(Triplet hostTriplet) {
        this.hostTriplet = hostTriplet;
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

    public List<String> getCompilerArgs() {
        return compilerArgs;
    }

    /**
     * Sets additional lists
     * @param compilerArgs a list of optional compiler arguments
     */
    public void setCompilerArgs(List<String> compilerArgs) {
        this.compilerArgs = compilerArgs;
    }

    public List<String> getLinkerArgs() {
        return linkerArgs;
    }

    /**
     * Sets an additional list of linker arguments that will be added to the linker command "as is",
     * without any form of validation on these arguments.
     * @param linkerArgs a list of additional linker arguments.
     */
    public void setLinkerArgs(List<String> linkerArgs) {
        this.linkerArgs = linkerArgs;
    }

    /**
     * Sets additional lists
     * @param runtimeArgs a list of optional runtime arguments
     */
    public void setRuntimeArgs(List<String> runtimeArgs) {
        this.runtimeArgs = runtimeArgs;
    }

    public List<String> getRuntimeArgs() {
        return runtimeArgs;
    }

    public String getAppId() {
        return appId;
    }

    /**
     * Sets the AppID. This acts as the application identifier in various platforms.
     * For Android, this is the equivalent of 'package' name of the application.
     *
     * @param appId The application ID of the application.
     */
    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppName() {
        return this.appName;
    }

    /**
     * Sets the app name
     * @param appName the name of the application (e.g. demo)
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getMainClassName() {
        // never null as it is required in constructor and there is no setter
        return mainClassName;
    }

    public String getClasspath() {
        // never null as it is required in constructor and there is no setter
        return classpath;
    }

    /**
     * Set the host name for remote deploying, typically to an
     * embedded system, providing it is reachable and SSH is
     * enabled.
     *
     * @param remoteHostName the name of the remote host name
     */
    public void setRemoteHostName(String remoteHostName) {
        this.remoteHostName = remoteHostName;
    }

    public String getRemoteHostName() {
        return remoteHostName;
    }

    /**
     * Sets the directory where the native image will be deployed
     * on the remote system, providing the remote host is reachable
     * and SSH is enabled.
     *
     * @param remoteDir a directory
     */
    public void setRemoteDir(String remoteDir) {
        this.remoteDir = remoteDir;
    }

    public String getRemoteDir() {
        return remoteDir;
    }

    public ReleaseConfiguration getReleaseConfiguration() {
        return releaseConfiguration;
    }

    /**
     * Sets some iOS and Android specific parameters that are required for
     * the release of mobile apps
     * @param releaseConfiguration release configuration
     */
    public void setReleaseConfiguration(ReleaseConfiguration releaseConfiguration) {
        this.releaseConfiguration = releaseConfiguration;
    }

    @Override
    public String toString() {
        return "ProjectConfiguration{" +
                "graalPath='" + graalPath + '\'' +
                ", javaStaticSdkVersion='" + javaStaticSdkVersion + '\'' +
                ", javafxStaticSdkVersion='" + javafxStaticSdkVersion + '\'' +
                ", usePrismSW=" + usePrismSW +
                ", verbose=" + verbose +
                ", targetTriplet=" + targetTriplet +
                ", hostTriplet=" + hostTriplet +
                ", bundlesList=" + bundlesList +
                ", resourcesList=" + resourcesList +
                ", reflectionList=" + reflectionList +
                ", jniList=" + jniList +
                ", compilerArgs=" + compilerArgs +
                ", runtimeArgs=" + runtimeArgs +
                ", appId='" + appId + '\'' +
                ", appName='" + appName + '\'' +
                ", releaseConfiguration='" + releaseConfiguration + '\'' +
                ", mainClassName='" + mainClassName + '\'' +
                ", classpath='" + classpath + '\'' +
                ", remoteHostName='" + remoteHostName + '\'' +
                ", remoteDir='" + remoteDir + '\'' +
                '}';
    }
}
