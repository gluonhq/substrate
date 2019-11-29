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
package com.gluonhq.substrate;

import com.gluonhq.substrate.model.IosSigningConfiguration;
import com.gluonhq.substrate.model.Triplet;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * This class contains all public configuration info about the current project (not about the current OS/Arch/vendor etc)
 * Instances of this class give hints to where real values should be contained from.
 */
public class ProjectConfiguration {

    private String graalPath;

    private String javafxStaticSdkVersion;
    private String javaStaticSdkVersion;

    private boolean usePrismSW = false;
    private boolean verbose = false;

    private Triplet targetTriplet;
    private Triplet hostTriplet = Triplet.fromCurrentOS();

    private List<String> bundlesList = Collections.emptyList();
    private List<String> resourcesList = Collections.emptyList();
    private List<String> reflectionList = Collections.emptyList();
    private List<String> jniList = Collections.emptyList();

    private String appName;
    private String mainClassName;

    private IosSigningConfiguration iosSigningConfiguration = new IosSigningConfiguration();

    public ProjectConfiguration( String mainClassName ) {
        this.mainClassName = Objects.requireNonNull(mainClassName, "Main class name is required")
                               .contains("/") ?
                                  mainClassName.substring( mainClassName.indexOf("/") + 1) : mainClassName;

    }

    public Path getGraalPath() {
        return Path.of( Objects.requireNonNull(this.graalPath, "GraalVM Path is not defined"));
    }

    public void setGraalPath(String path) {
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
        return Objects.requireNonNull( this.javaStaticSdkVersion, "Java Static SDK version is required" );
    }

    /**
     * Sets the JavaFX static SDK version
     * @param javafxStaticSdkVersion the JavaFX static SDK version
     */
    public void setJavafxStaticSdkVersion(String javafxStaticSdkVersion) {
        this.javafxStaticSdkVersion = javafxStaticSdkVersion;
    }

    public String getJavafxStaticSdkVersion() {
        return Objects.requireNonNull( this.javafxStaticSdkVersion, "JavaFX Static SDK version is required" );
    }


    public boolean isUsePrismSW() {
        return usePrismSW;
    }

    public void setUsePrismSW(boolean usePrismSW) {
        this.usePrismSW = usePrismSW;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public Triplet getTargetTriplet() {
        return Objects.requireNonNull( targetTriplet, "Target triplet is required") ;
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
        this.hostTriplet = hostTriplet == null? Triplet.fromCurrentOS(): hostTriplet;
    }

    public List<String> getBundlesList() {
        return bundlesList;
    }

    /**
     * Sets additional bundles
     * @param bundlesList a list of classes that will be added to the default bundlesList list
     */
    public void setBundlesList(List<String> bundlesList) {
        this.bundlesList = bundlesList == null? Collections.emptyList(): bundlesList;
    }

    /**
     * Set additional resources to be included
     * @param resourcesList a list of resource patterns that will be included
     */
    public void setResourcesList(List<String> resourcesList) {
        this.resourcesList = resourcesList == null? Collections.emptyList(): resourcesList;
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
        this.reflectionList = reflectionList == null? Collections.emptyList(): reflectionList;
    }

    public List<String> getJniList() {
        return jniList;
    }

    /**
     * Sets additional lists
     * @param jniList a list of classes that will be added to the default jni list
     */
    public void setJniList(List<String> jniList) {
        this.jniList = jniList == null? Collections.emptyList(): jniList;
    }

    public String getAppName() {
        return Objects.requireNonNull(appName, "App name is required");
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

    public IosSigningConfiguration getIosSigningConfiguration() {
        return iosSigningConfiguration;
    }

    /**
     * Sets some iOS specific parameters
     * @param iosSigningConfiguration iOS configuration
     */
    public void setIosSigningConfiguration(IosSigningConfiguration iosSigningConfiguration) {
        this.iosSigningConfiguration = iosSigningConfiguration == null?
                new IosSigningConfiguration(): iosSigningConfiguration;
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
                ", appName='" + appName + '\'' +
                ", iosConfiguration='" + iosSigningConfiguration + '\'' +
                ", mainClassName='" + mainClassName + '\'' +
                '}';
    }
}