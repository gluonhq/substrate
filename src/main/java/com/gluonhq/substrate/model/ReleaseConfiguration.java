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
package com.gluonhq.substrate.model;

public class ReleaseConfiguration {

    public static final String DEFAULT_BUNDLE_VERSION = "1.0";
    public static final String DEFAULT_BUNDLE_SHORT_VERSION = "1.0";

    public static final String DEFAULT_CODE_VERSION = "1";
    public static final String DEFAULT_CODE_NAME = "1.0";
    public static final String DEFAULT_DEBUG_KEY_STORE_PASSWORD = "android";
    public static final String DEFAULT_DEBUG_KEY_ALIAS = "androiddebugkey";
    public static final String DEFAULT_DEBUG_KEY_ALIAS_PASSWORD = "android";

    // iOS

    /**
     * A user-visible short name for the bundle
     *
     * Default: if not set, $appName will be used.
     */
    private String bundleName;

    /**
     * The version of the build that identifies an iteration of the bundle. A
     * string composed of one to three period-separated integers, containing
     * numeric characters (0-9) and periods only.
     *
     * Default: 1.0
     */
    private String bundleVersion;

    /**
     * A user-visible string for the release or version number of the bundle. A
     * string composed of one to three period-separated integers, containing
     * numeric characters (0-9) and periods only.
     *
     * Default: 1.0
     */
    private String bundleShortVersion;

    /**
     * String that identifies a valid certificate that will be used for iOS development
     * or iOS distribution.
     *
     * Default: null. When not provided, Substrate will be selected from all the valid identities found
     * installed on the machine from any of these types:
     *
     *      iPhone Developer|Apple Development|iOS Development|iPhone Distribution
     *
     * and that were used by the provisioning profile.
     */
    private String providedSigningIdentity;

    /**
     * String with the name of the provisioning profile created for iOS development or
     * distribution of the given app.
     *
     * Default: null. When not provided, Substrate will try to find a valid installed
     * provisioning profile that can be used to sign the app, including wildcards.
     */
    private String providedProvisioningProfile;

    /**
     * Boolean that can be used to skip signing iOS apps. This will prevent any
     * deployment, but can be useful to run tests without an actual device
     */
    private boolean skipSigning;

    /**
     * A string with a valid name of an iOS simulator device
     */
    private String simulatorDevice;

    // Android

    /**
     * A user-visible short name for the app
     *
     * Default: if not set, $appName will be used.
     */
    private String appLabel;

    /**
     * A positive integer used as an internal version number
     *
     * Default: 1
     */
    private String versionCode;

    /**
     * A string used as the version number shown to users, like
     * <major>.<minor>.<point>
     *
     * Default: 1.0
     */
    private String versionName;

    /**
     * A string with the path to a keystore file that can be used to sign
     * the Android apk.
     *
     * Default: null. If not set, Substrate creates and uses a debug keystore.
     */
    private String providedKeyStorePath;

    /**
     * A string with the password of the provide keystore file.
     *
     * Default: null. If not set, Substrate creates and uses a debug keystore.
     */
    private String providedKeyStorePassword;

    /**
     * A string with an identifying name for the key
     *
     * Default: null. If not set, Substrate creates and uses a debug keystore.
     */
    private String providedKeyAlias;

    /**
     * A string with a password for the key
     *
     * Default: null. If not set, Substrate creates and uses a debug keystore.
     */
    private String providedKeyAliasPassword;

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public String getBundleVersion() {
        return bundleVersion;
    }

    public void setBundleVersion(String bundleVersion) {
        this.bundleVersion = bundleVersion;
    }

    public String getBundleShortVersion() {
        return bundleShortVersion;
    }

    public void setBundleShortVersion(String bundleShortVersion) {
        this.bundleShortVersion = bundleShortVersion;
    }

    public String getProvidedSigningIdentity() {
        return providedSigningIdentity;
    }

    public void setProvidedSigningIdentity(String providedSigningIdentity) {
        this.providedSigningIdentity = providedSigningIdentity;
    }

    public String getProvidedProvisioningProfile() {
        return providedProvisioningProfile;
    }

    public void setProvidedProvisioningProfile(String providedProvisioningProfile) {
        this.providedProvisioningProfile = providedProvisioningProfile;
    }

    public boolean isSkipSigning() {
        return skipSigning;
    }

    public void setSkipSigning(boolean skipSigning) {
        this.skipSigning = skipSigning;
    }

    public String getSimulatorDevice() {
        return simulatorDevice;
    }

    public void setSimulatorDevice(String simulatorDevice) {
        this.simulatorDevice = simulatorDevice;
    }

    public String getAppLabel() {
        return appLabel;
    }

    public void setAppLabel(String appLabel) {
        this.appLabel = appLabel;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(String versionCode) {
        this.versionCode = versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getProvidedKeyStorePath() {
        return providedKeyStorePath;
    }

    public void setProvidedKeyStorePath(String providedKeyStorePath) {
        this.providedKeyStorePath = providedKeyStorePath;
    }

    public String getProvidedKeyStorePassword() {
        return providedKeyStorePassword;
    }

    public void setProvidedKeyStorePassword(String providedKeyStorePassword) {
        this.providedKeyStorePassword = providedKeyStorePassword;
    }

    public String getProvidedKeyAlias() {
        return providedKeyAlias;
    }

    public void setProvidedKeyAlias(String providedKeyAlias) {
        this.providedKeyAlias = providedKeyAlias;
    }

    public String getProvidedKeyAliasPassword() {
        return providedKeyAliasPassword;
    }

    public void setProvidedKeyAliasPassword(String providedKeyAliasPassword) {
        this.providedKeyAliasPassword = providedKeyAliasPassword;
    }

    @Override
    public String toString() {
        return "ReleaseConfiguration{" +
                "bundleName='" + bundleName + '\'' +
                ", bundleVersion='" + bundleVersion + '\'' +
                ", bundleShortVersion='" + bundleShortVersion + '\'' +
                ", providedSigningIdentity='" + providedSigningIdentity + '\'' +
                ", providedProvisioningProfile='" + providedProvisioningProfile + '\'' +
                ", skipSigning=" + skipSigning +
                ", simulatorDevice='" + simulatorDevice + '\'' +
                ", appLabel='" + appLabel + '\'' +
                ", versionCode='" + versionCode + '\'' +
                ", versionName='" + versionName + '\'' +
                ", providedKeyStorePath='" + providedKeyStorePath + '\'' +
                ", providedKeyStorePassword='" + providedKeyStorePassword + '\'' +
                ", providedKeyAlias='" + providedKeyAlias + '\'' +
                ", providedKeyAliasPassword='" + providedKeyAliasPassword + '\'' +
                '}';
    }
}
