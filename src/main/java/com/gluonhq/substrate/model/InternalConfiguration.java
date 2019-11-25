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
public class InternalConfiguration {

    final ProjectConfiguration publicConfiguration;

    private String graalPath = null;

    private String javaStaticLibs = null;
    private String javaStaticSdkVersion = null;

    private String javaFXStaticSDK = null;
    private String javafxStaticSdkVersion = null;

    private boolean useJavaFX = false; // only used for caching
    private String llcPath = null; //only used for caching

    public InternalConfiguration(ProjectConfiguration config) {
        this.publicConfiguration = config;
    }

    // ============= direct access to public Configuration =====

    public String getMainClassName() {
        return publicConfiguration.getMainClassName();
    }

    public String getAppName() {
        return publicConfiguration.getAppName();
    }

    public Triplet getTargetTriplet() {
        return publicConfiguration.getTargetTriplet();
    }

    public Triplet getHostTriplet() {
        return publicConfiguration.getHostTriplet();
    }

    public boolean isVerbose() {
        return publicConfiguration.isVerbose();
    }

    public boolean isUsePrismSW() {
        return publicConfiguration.isUsePrismSW();
    }

    public List<String> getResourcesList() {
        return publicConfiguration.getResourcesList();
    }

    public List<String> getReflectionList() {
        return publicConfiguration.getReflectionList();
    }

    public List<String> getBundlesList() {
        return publicConfiguration.getBundlesList();
    }

    public List<String> getJniList() {
        return publicConfiguration.getJniList();
    }


    public IosSigningConfiguration getIosSigningConfiguration() {
        return publicConfiguration.getIosSigningConfiguration();
    }


    // ============== indirectly influenced via public Configuration =====
    public String getGraalPath() {
        if (graalPath == null) {
            if (publicConfiguration.getGraalPath()!= null) {
                this.graalPath = publicConfiguration.getGraalPath();
            } else {
                this.graalPath = System.getenv("GRAALVM_HOME");
            }
        }
        return graalPath;
    }


    /**
     * Returns the version string for the static JDK libs.
     * If this has not been specified before, the value will be calculated here on the following rule:
     * If the public configuration has set the version, that will be used. Otherwise, the default will be
     * returned.
     * @return the specified JavaStaticSDK version, or the default
     */
    public String getJavaStaticSdkVersion() {
        if (javaStaticSdkVersion == null) {
            if (publicConfiguration.getJavaStaticSdkVersion() != null) {
                javaStaticSdkVersion = publicConfiguration.getJavaStaticSdkVersion();
            } else {
                javaStaticSdkVersion = Constants.DEFAULT_JAVA_STATIC_SDK_VERSION;
            }
        }
        return javaStaticSdkVersion;
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
    /**
     * Returns the version string for the static JavaFX libs.
     * If this has not been specified before, the value will be calculated here on the following rule:
     * If the public configuration has set the version, that will be used. Otherwise, the default will be
     * returned.
     * @return the specified JavaFXStaticSDK version, or the default
     */
    public String getJavafxStaticSdkVersion() {
        if (javafxStaticSdkVersion == null) {
            if (publicConfiguration.getJavafxStaticSdkVersion() != null) {
                javafxStaticSdkVersion = publicConfiguration.getJavafxStaticSdkVersion();
            } else {
                javafxStaticSdkVersion = Constants.DEFAULT_JAVAFX_STATIC_SDK_VERSION;
            }
        }
        return javafxStaticSdkVersion;
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


    // ============== not obtained via public Configuration =====

    public boolean isUseJavaFX() {
        return useJavaFX;
    }

    public void setUseJavaFX(boolean useJavaFX) {
        this.useJavaFX = useJavaFX;
    }

    public boolean isEnableCheckHash() {
        return true;
    }

    public void setLlcPath(String v) {
        this.llcPath = v;
    }

    public String getLlcPath() {
        return this.llcPath;
    }

}
