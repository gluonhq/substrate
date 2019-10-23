/*
 * Copyright (c) 2019, Gluon
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
package com.gluonhq.substrate.util.ios;

import com.gluonhq.substrate.util.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public class MobileProvision {

    public static final String MOBILE_PROVISION_EXTENSION = ".mobileprovision";

    public enum Profile {
        DEVELOPMENT ("Development", "Deploy via Xcode"),
        DISTRIBUTION_AD_HOC("AdHoc", "Distribute via TestFlight"),
        DISTRIBUTION_APP_STORE("AppStore", "Distribute via App Store");

        private final String name;
        private final String description;

        Profile(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    private final Path provisioningPath;

    private String appIdName;
    private String appIdentifierPrefix;
    private LocalDate creationDate;
    private String platform;
    private boolean xcodeManaged;
    private List<String> developerCertificates;

    private NSDictionaryEx entitlements;
    private boolean betaReports;
    private String appIdentifier;
    private String keychainAccess;
    private boolean taskAllow;
    private String devTeamIdentifier;
    private String apsEnvironment;

    private LocalDate expirationDate;
    private String name;
    private List<String> provisionedDevices;
    private String teamIdentifier;
    private String teamName;
    private int timeToLive;
    private String uuid;
    private int version;

    private Profile profile;

    public MobileProvision(Path provisioningPath) {
        this.provisioningPath = provisioningPath;
        processMobileProvision();
    }

    private void processMobileProvision() {
        if (!Files.exists(provisioningPath) ||
                !provisioningPath.toFile().getName().endsWith(MOBILE_PROVISION_EXTENSION)) {
            Logger.logDebug("Invalid mobile provisioning profile for " + provisioningPath);
            return;
        }
        Logger.logDebug("Mobile provisioning profile for " + provisioningPath);

        // To get a dump of a mobile provision file on terminal:
        // security cms -D -i <provisioningPath>

        // This will read the provisioning profile and return a dictionary:
        NSDictionaryEx dictionary = NSDictionaryEx.dictionaryFromProvisioningPath(provisioningPath);

        // Extract all the keys:
        this.appIdName = dictionary.getString("AppIDName");
        this.appIdentifierPrefix = dictionary.getFirstString("ApplicationIdentifierPrefix");
        this.creationDate = dictionary.getDate("CreationDate");
        this.platform = dictionary.getFirstString("Platform");
        this.xcodeManaged = dictionary.getBoolean("IsXcodeManaged");
        // get encoded Developer certificates
        this.developerCertificates = NSDictionaryEx.certificates(dictionary.getArray("DeveloperCertificates"));

        this.entitlements = dictionary.getDictionary("Entitlements");
        this.betaReports = entitlements.getBoolean("beta-reports-active");
        this.appIdentifier = entitlements.getString("application-identifier");
        this.keychainAccess = entitlements.getFirstString("keychain-access-groups");
        this.taskAllow = entitlements.getBoolean("get-task-allow");
        this.devTeamIdentifier = entitlements.getString("com.apple.developer.team-identifier");
        this.apsEnvironment = entitlements.getString("aps-environment");

        this.expirationDate = dictionary.getDate("ExpirationDate");
        this.name = dictionary.getString("Name");
        this.provisionedDevices = dictionary.getArrayString("ProvisionedDevices");
        this.teamIdentifier = dictionary.getFirstString("TeamIdentifier");
        this.teamName = dictionary.getString("TeamName");
        this.timeToLive = dictionary.getInteger("TimeToLive");
        this.uuid = dictionary.getString("UUID");
        this.version = dictionary.getInteger("Version");

        this.profile = taskAllow ? Profile.DEVELOPMENT :
                provisionedDevices != null ? Profile.DISTRIBUTION_AD_HOC :
                        Profile.DISTRIBUTION_APP_STORE;
    }

    public Path getProvisioningPath() {
        return provisioningPath;
    }

    public String getAppIdName() {
        return appIdName;
    }

    public String getAppIdentifierPrefix() {
        return appIdentifierPrefix;
    }

    public LocalDate getCreationDate() {
        return creationDate;
    }

    public String getPlatform() {
        return platform;
    }

    public boolean isXcodeManaged() {
        return xcodeManaged;
    }

    public List<String> getDeveloperCertificates() {
        return developerCertificates;
    }

    public NSDictionaryEx getEntitlements() {
        return entitlements;
    }

    public boolean isBetaReports() {
        return betaReports;
    }

    public String getAppIdentifier() {
        return appIdentifier;
    }

    public String getKeychainAccess() {
        return keychainAccess;
    }

    public boolean isTaskAllow() {
        return taskAllow;
    }

    public String getDevTeamIdentifier() {
        return devTeamIdentifier;
    }

    public String getApsEnvironment() {
        return apsEnvironment;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public String getName() {
        return name;
    }

    public List<String> getProvisionedDevices() {
        return provisionedDevices;
    }

    public String getTeamIdentifier() {
        return teamIdentifier;
    }

    public String getTeamName() {
        return teamName;
    }

    public int getTimeToLive() {
        return timeToLive;
    }

    public String getUuid() {
        return uuid;
    }

    public int getVersion() {
        return version;
    }

    public Profile getProfile() {
        return profile;
    }

    @Override
    public String toString() {
        return "MobileProvision{" +
                "provisioningPath=" + provisioningPath +
                ", appIdName='" + appIdName + '\'' +
                ", appIdentifierPrefix='" + appIdentifierPrefix + '\'' +
                ", creationDate=" + creationDate +
                ", platform='" + platform + '\'' +
                ", xcodeManaged=" + xcodeManaged +
                ", developerCertificates=" + developerCertificates +
                ", entitlements=" + entitlements +
                ", betaReports=" + betaReports +
                ", appIdentifier='" + appIdentifier + '\'' +
                ", keychainAccess='" + keychainAccess + '\'' +
                ", taskAllow=" + taskAllow +
                ", devTeamIdentifier='" + devTeamIdentifier + '\'' +
                ", apsEnvironment='" + apsEnvironment + '\'' +
                ", expirationDate=" + expirationDate +
                ", name='" + name + '\'' +
                ", provisionedDevices=" + provisionedDevices +
                ", teamIdentifier='" + teamIdentifier + '\'' +
                ", teamName='" + teamName + '\'' +
                ", timeToLive=" + timeToLive +
                ", uuid='" + uuid + '\'' +
                ", version=" + version +
                ", profile=" + profile +
                '}';
    }
}
