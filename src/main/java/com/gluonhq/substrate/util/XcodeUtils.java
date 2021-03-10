package com.gluonhq.substrate.util;

import com.gluonhq.substrate.util.ios.NSDictionaryEx;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static com.gluonhq.substrate.Constants.GLUON_SUBSTRATE;

public class XcodeUtils {

    public enum SDKS {
        MACOSX ("MacOSX"),
        IPHONEOS ("iPhoneOS"),
        IPHONESIMULATOR ("iPhoneSimulator");

        private final String sdkName;
        private String absolutePath;

        SDKS(String sdkName) {
            this.sdkName = sdkName;
        }

        public String getSDKPath() {
            if (absolutePath == null) {
                absolutePath = getSdkDir(sdkName.toLowerCase(Locale.ROOT));
            }
            return absolutePath;
        }

        public String getSdkName() {
            return sdkName;
        }

        private String getSdkDir(String name) {
            try {
                return ProcessRunner.runProcessForSingleOutput("sdk", "xcrun", "--sdk", name, "--show-sdk-path");
            } catch (IOException | InterruptedException e) {
                Logger.logFatal(e, "Error retrieving sdk for " + name + ":" + e.getMessage());
            }
            return null;
        }
    }

    public static final Path XCODE_PRODUCTS_PATH = Paths.get(System.getProperty("user.home")).
            resolve("Library/Developer/Xcode/DerivedData/" + GLUON_SUBSTRATE + "/Build/Products/");

    private final SDKS sdk;

    private String platformBuild;
    private String platformVersion;
    private String platformName;
    private String dtxcode;
    private String dtxcodeBuild;
    private String buildMachineOSBuild;
    private String sdkName;

    public XcodeUtils(SDKS sdk) throws IOException {
        this.sdk = sdk;
        String root = sdk.getSDKPath();

        Path rootDir = Paths.get(root);
        Path systemVersionFile = rootDir.resolve("System/Library/CoreServices/SystemVersion.plist");
        Path sdkSettingsFile   = rootDir.resolve("SDKSettings.plist");
        Path platformInfoFile  = rootDir.getParent().getParent().getParent().resolve("Info.plist");
        Path xcodeInfoFile     = rootDir.getParent().getParent().getParent().getParent().getParent().getParent().resolve("Info.plist");
        Path xcodeVersionFile  = rootDir.getParent().getParent().getParent().getParent().getParent().getParent().resolve("version.plist");
        Logger.logDebug("platform info file at " + platformInfoFile);
        try {
            NSDictionaryEx systemVersionDict = new NSDictionaryEx(systemVersionFile);
            NSDictionaryEx platformInfoDict  = new NSDictionaryEx(platformInfoFile);
            NSDictionaryEx xcodeInfoDict     = new NSDictionaryEx(xcodeInfoFile);
            NSDictionaryEx xcodeVersionDict  = new NSDictionaryEx(xcodeVersionFile);
            NSDictionaryEx sdkSettingsDict   = new NSDictionaryEx(sdkSettingsFile);

            platformBuild = systemVersionDict.getString("ProductBuildVersion");

            NSDictionaryEx additionalInfo =  platformInfoDict.getDictionary("AdditionalInfo");
            this.platformVersion = additionalInfo.getString("DTPlatformVersion");
            this.platformName    = additionalInfo.getString("DTPlatformName");
            this.dtxcode         = xcodeInfoDict.getString("DTXcode");
            this.dtxcodeBuild    = xcodeVersionDict.getString("ProductBuildVersion");
            this.buildMachineOSBuild = xcodeInfoDict.getString("BuildMachineOSBuild");
            this.sdkName         = sdkSettingsDict.getString( "CanonicalName");
        } catch (Exception ex) {
            Logger.logFatal(ex, "Error processing plist file");
        }

    }

    public SDKS getSdk() {
        return sdk;
    }

    public String getPlatformBuild() {
        return platformBuild;
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    public String getDTXcode() {
        return dtxcode;
    }

    public String getDTXcodeBuild() {
        return dtxcodeBuild;
    }

    public String getBuildMachineOSBuild() {
        return buildMachineOSBuild;
    }

    public String getPlatformName() {
        return platformName;
    }

    public String getSDKName() {
        return sdkName;
    }

    public static String getCommandForSdk(String command, String sdk) throws IOException, InterruptedException {
        return ProcessRunner.runProcessForSingleOutput("xcrun", "xcrun", "-sdk", sdk, "-f", command);
    }
}
