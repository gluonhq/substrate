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
package com.gluonhq.substrate.util;

import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.InternalProjectConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class FileDeps {

    private static final String JAVA_STATIC_ZIP = "labs-staticjdk-${target}-gvm-${version}.zip";
    private static final String JAVA_STATIC_URL = "https://download2.gluonhq.com/substrate/staticjdk/";
    private static final String JAVAFX_STATIC_ZIP = "openjfx-${version}-${target}-static.zip";
    private static final String JAVAFX_STATIC_URL = "https://download2.gluonhq.com/substrate/javafxstaticsdk/";

    private static final List<String> JAVA_FILES = Arrays.asList(
            "libjava.a", "libnet.a", "libnio.a", "libzip.a", "libprefs.a"
    );

    private static final List<String> JAVAFX_FILES = Arrays.asList(
            "javafx.base.jar", "javafx.controls.jar", "javafx.graphics.jar",
            "javafx.fxml.jar", "javafx.media.jar", "javafx.web.jar"
    );

    private static final List<String> JAVAFX_STATIC_FILES = Arrays.asList(
            "libglass.a", "libglass_monocle.a"
    );

    private static final String ANDROID_SDK_URL = "https://dl.google.com/android/repository/sdk-tools-${host}-4333796.zip";

    private static final String[] ANDROID_DEPS = {
            "https://repo1.maven.org/maven2/javax/activation/activation/1.1.1/activation-1.1.1.jar",
            "https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-xjc/2.3.2/jaxb-xjc-2.3.2.jar",
            "https://repo1.maven.org/maven2/com/sun/xml/bind/jaxb-impl/2.3.0.1/jaxb-impl-2.3.0.1.jar",
            "https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-core/2.3.0.1/jaxb-core-2.3.0.1.jar",
            "https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-jxc/2.3.2/jaxb-jxc-2.3.2.jar",
            "https://repo1.maven.org/maven2/javax/xml/bind/jaxb-api/2.3.1/jaxb-api-2.3.1.jar",
            "https://repo1.maven.org/maven2/com/sun/istack/istack-commons-runtime/3.0.10/istack-commons-runtime-3.0.10.jar" };

    private static final String ANDROID_KEY = "24333f8a63b6825ea9c5514f83c2829b004d1fee";
    private static final String[] ANDROID_SDK_PACKAGES = {
            "platforms;android-28", "build-tools;29.0.2", "platform-tools",
            "extras;android;m2repository", "extras;google;m2repository", "ndk-bundle" };

    private final InternalProjectConfiguration configuration;

    public FileDeps(InternalProjectConfiguration config) {
        this.configuration = Objects.requireNonNull(config);
    }

    /**
     * Returns the path to the Java SDK static libraries for this configuration. The path is cached on the provided
     * configuration. If no custom directory has been set in the project configuration and <code>useGraalPath</code>
     * is set to <code>true</code>, it will use the <code>lib/static/$target-$arch/</code> directory inside the
     * configured Graal path. If <code>useGraalPath</code> is set to <code>false</code>, a custom Java SDK will
     * be downloaded.
     *
     * @param useGraalPath specifies if the default Java SDK path should resolve to the Graal installation dir
     * @return the location of the static libraries of the Java SDK for the arch-os for this configuration
     * @throws IOException in case anything goes wrong.
     */
    public Path getJavaSDKLibsPath(boolean useGraalPath) throws IOException {
        if (!configuration.useCustomJavaStaticLibs() && useGraalPath) {
            Path graalSDKLibsPath = configuration.getGraalPath().resolve("lib").resolve("static").resolve(configuration.getTargetTriplet().getOsArch2());
            if (Constants.OS_LINUX.equals(configuration.getTargetTriplet().getOs())) {
                return graalSDKLibsPath.resolve("glibc");
            } else {
                return graalSDKLibsPath;
            }
        }
        return resolvePath(configuration.getJavaStaticLibsPath(),"Fatal error, could not install Java SDK ");
    }

    /**
     * Return the path to the JavaFX SDK for this configuration.
     * The path is cached on the provided configuration.
     * If it is not there yet, all dependencies are retrieved.
     * @return the location of the JavaFX SDK for the arch-os for this configuration
     * @throws IOException in case anything goes wrong.
     */
    public Path getJavaFXSDKLibsPath() throws IOException {
        return resolvePath(configuration.getJavafxStaticLibsPath(),"Fatal error, could not install JavaFX SDK ");
    }

    /**
     * Return the path to the Android SDK for this configuration.
     * The path is cached on the environment variable.
     * If it is not there yet, all dependencies are retrieved.
     * @return the location of the Android SDK for the arch-os for this configuration
     * @throws IOException in case anything goes wrong.
     */
    public Path getAndroidSDKPath() throws IOException {
        return resolvePath(configuration.getAndroidSdkPath(),"Fatal error, could not install Android SDK ");
    }

    /**
     * Return the path to the Android NDK for this configuration.
     * The path is cached on the environment variable.
     * If it is not there yet, all dependencies are retrieved.
     * @return the location of the Android NDK for the arch-os for this configuration
     * @throws IOException in case anything goes wrong.
     */
    public Path getAndroidNDKPath() throws IOException {
        return resolvePath(configuration.getAndroidNdkPath(),"Fatal error, could not install Android NDK ");
    }

    /**
     * For a given path, it verifies that the path exists, or else it tries
     * to install it. After that, it returns the path for this configuration.
     * @param path the initial path
     * @param errorMessage a message that will be displayed in case of error
     * @return the location of the path for this configuration
     * @throws IOException in case anything goes wrong.
     */
    private Path resolvePath(Path path, String errorMessage) throws IOException {
        if (Files.exists(Objects.requireNonNull(path))) {
            return path;
        }
        if (!setupDependencies()) {
            throw new IOException(errorMessage); //"Error setting up dependencies"
        }
        return path;
    }

    /**
     * First, this method searches for a valid location of the java static libraries
     * (e.g. libjava.a). When a user-supplied location is present, this location will be
     * used to check for the presence of those libraries. If the user-supplied location is
     * present, but the libraries are not there, an <code>IOException</code> is thrown.
     *
     * If no custom location has been specified, the default location for the static libs is used.
     * If no libs are found on the default location, they are downloaded and unzipped (TBD!!!)
     *
     * Verifies if Java static SDK and JavaFX static SDK (when using JavaFX) are present at
     * the default location, and contain an unmodified set of files.
     * If this is not the case, the correct SDK is downloaded and unzipped.
     *
     * @return true if the processed ended succesfully, false otherwise
     * @throws IOException in case default path for Substrate dependencies can't be created
     */
    private boolean setupDependencies() throws IOException {
        String target = configuration.getTargetTriplet().getOsArch();

        if (!Files.isDirectory(Constants.USER_SUBSTRATE_PATH)) {
            Files.createDirectories(Constants.USER_SUBSTRATE_PATH);
        }

        Path javaStaticLibs = configuration.getJavaStaticLibsPath();
        Path defaultJavaStaticPath = configuration.getDefaultJavaStaticPath();
        boolean customJavaLocation = configuration.useCustomJavaStaticLibs();

        boolean downloadJavaStatic = false;
        boolean downloadJavaFXStatic = false;
        boolean downloadAndroidSdk = false;
        boolean downloadAndroidNdk = false;
        boolean downloadAndroidAdditionalLibs = false;

        // Java Static
        Logger.logDebug("Processing JavaStatic dependencies at " + javaStaticLibs.toString());

        if ((configuration.isUseJNI()) && (!configuration.getHostTriplet().equals(configuration.getTargetTriplet()))) {
            if (!Files.isDirectory(javaStaticLibs)) {
                System.err.println("Not a dir");
                if (customJavaLocation) {
                    throw new IOException ("A location for the static sdk libs was supplied, but it doesn't exist: "+javaStaticLibs);
                }
                downloadJavaStatic = true;
            } else {
                String path = javaStaticLibs.toString();
                if (JAVA_FILES.stream()
                        .map(s -> new File(path, s))
                        .anyMatch(f -> !f.exists())) {
                    Logger.logDebug("jar file not found");
                    System.err.println("jar not found");
                    if (customJavaLocation) {
                        throw new IOException ("A location for the static sdk libs was supplied, but the java libs are missing "+javaStaticLibs);
                    }
                    downloadJavaStatic = true;
                } else if (!customJavaLocation && configuration.isEnableCheckHash()) {
                    // when the directory for the libs is found, and it is not a user-supplied one, check for its validity
                    Logger.logDebug("Checking java static sdk hashes");
                    String md5File = getChecksumFileName(defaultJavaStaticPath, "javaStaticSdk", target);
                    Map<String, String> hashes = FileOps.getHashMap(md5File);
                    if (hashes == null) {
                        Logger.logDebug(md5File+" not found");
                        downloadJavaStatic = true;
                    } else if (JAVA_FILES.stream()
                            .map(s -> new File(path, s))
                            .anyMatch(f -> !hashes.get(f.getName()).equals(FileOps.calculateCheckSum(f)))) {
                        Logger.logDebug("jar file has invalid hashcode");
                        downloadJavaStatic = true;
                    }
                }
            }
        }

        // JavaFX Static
        if (configuration.isUseJavaFX()) {
            Path javafxStatic = configuration.getJavafxStaticLibsPath();
            Logger.logDebug("Processing JavaFXStatic dependencies at " + javafxStatic.toString());

            if (!Files.isDirectory(javafxStatic)) {
         //       Logger.logDebug("javafxStaticSdk/" + configuration.getJavafxStaticSdkVersion() + "/" + target + "-sdk/lib folder not found");
                downloadJavaFXStatic = true;
            } else {
                String path = javafxStatic.toString();
                if (JAVAFX_FILES.stream().map(s -> new File(path, s)).anyMatch(f -> !f.exists()) ||
                        JAVAFX_STATIC_FILES.stream().map(s -> new File(path, s)).noneMatch(File::exists)) {
                    Logger.logDebug("JavaFX file not found");
                    downloadJavaFXStatic = true;
                } else if (configuration.isEnableCheckHash()) {
                    Logger.logDebug("Checking javafx static sdk hashes");
                    String md5File = getChecksumFileName(javafxStatic.getParent(), "javafxStaticSdk", target);
                    Map<String, String> hashes = FileOps.getHashMap(md5File);
                    if (hashes == null) {
                        Logger.logDebug(md5File + " md5 not found");
                        downloadJavaFXStatic = true;
                    } else if (JAVAFX_FILES.stream()
                            .map(s -> new File(path, s))
                            .anyMatch(f -> !hashes.get(f.getName()).equals(FileOps.calculateCheckSum(f)))) {
                        Logger.logDebug("JavaFX jar file has invalid hashcode");
                        downloadJavaFXStatic = true;
                    }
                }
            }
        }
        // Android
        if (Constants.OS_ANDROID.equals(configuration.getTargetTriplet().getOs())) {
            Path androidSdk = configuration.getAndroidSdkPath();
            Path androidNdk = configuration.getAndroidNdkPath();

            Path libsLocation = androidSdk.resolve("tools").resolve("lib").resolve("java11");

            if (!Files.exists(androidSdk)) {
                Logger.logInfo("ANDROID_SDK not found and will be downloaded.");
                downloadAndroidSdk = true;
            }

            if (!Files.exists(libsLocation)) {
                downloadAndroidAdditionalLibs = true;
            }

            if (!Files.exists(androidNdk)) {
                Logger.logInfo("ANDROID_NDK not found and will be downloaded.");
                downloadAndroidNdk = true;
            }
        }
        try {
            if (downloadJavaStatic) {
                downloadJavaZip(target);
            }

            if (downloadJavaFXStatic) {
                downloadJavaFXZip(target);
            }

            if (downloadAndroidSdk) { // First we get SDK
                downloadAndroidSdkZip();
            }

            if (downloadAndroidAdditionalLibs) { // Then we get additional libs
                downloadAdditionalAndroidLibs();
            }

            if (downloadAndroidNdk) { // And then NDK
                fetchFromSdkManager();
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error downloading zips: " + e.getMessage());
        }
        Logger.logDebug("Setup dependencies done");

        if (!Files.exists(javaStaticLibs) && (!configuration.getHostTriplet().equals(configuration.getTargetTriplet()))) {
            Logger.logSevere("Error: path " + javaStaticLibs + " doesn't exist but required for crosscompilation");
            return false;
        }
        if (configuration.isUseJavaFX() && !Files.exists(configuration.getJavafxStaticLibsPath())) {
            Logger.logSevere("Error: path " + configuration.getJavafxStaticLibsPath() + " doesn't exist");
            return false;
        }
        return true;
    }

    /**
     * Generates standardized checksum file name for a given os architecture
     * @param base base path, parent of which will be used
     * @param customPart custom part of the name
     * @param osArch os architecture
     * @return
     */
    private static String getChecksumFileName(Path base, String customPart, String osArch) {
        return base.getParent().resolve(String.format("%s-%s.md5", customPart, osArch)).toString();
    }

    private void downloadJavaZip(String target) throws IOException {
        Logger.logInfo("Downloading Java Static Libs...");
        String javaZip = Strings.substitute(JAVA_STATIC_ZIP, Map.of(
            "version", configuration.getJavaStaticSdkVersion(),
            "target", target));
        FileOps.downloadAndUnzip(JAVA_STATIC_URL + javaZip,
                Constants.USER_SUBSTRATE_PATH,
                javaZip,
                "javaStaticSdk",
                configuration.getJavaStaticSdkVersion(),
                configuration.getTargetTriplet().getOsArch());
        Logger.logInfo("Java static libs downloaded successfully");
    }

    private void downloadJavaFXZip(String osarch) throws IOException {
        Logger.logInfo("Downloading JavaFX static libs...");
        String javafxZip = Strings.substitute(JAVAFX_STATIC_ZIP, Map.of(
            "version", configuration.getJavafxStaticSdkVersion(),
            "target", osarch));
        FileOps.downloadAndUnzip(JAVAFX_STATIC_URL + javafxZip,
                Constants.USER_SUBSTRATE_PATH,
                javafxZip,
                "javafxStaticSdk",
                configuration.getJavafxStaticSdkVersion(),
                configuration.getTargetTriplet().getOsArch());
        Logger.logInfo("JavaFX static libs downloaded successfully");
    }

    /**
     * Crafts Android SDK url and then downloads it
     * @throws IOException in case anything goes wrong.
     */
    private void downloadAndroidSdkZip() throws IOException {
        Logger.logInfo("Downloading Android SDK...");
        Path sdk = configuration.getAndroidSdkPath();
        String hostOs = configuration.getHostTriplet().getOs();
        String androidSdkUrl = Strings.substitute(ANDROID_SDK_URL, Map.of("host", hostOs));
        FileOps.downloadAndUnzip(androidSdkUrl, sdk.getParent(), "android-sdk.zip", sdk.getFileName().toString(), "");
        Logger.logInfo("Android SDK downloaded successfully");
    }
    /**
     * Downloads libraries needed for Android SDK's sdkmanager
     * @throws IOException in case anything goes wrong.
     */
    private void downloadAdditionalAndroidLibs() throws IOException {
        Logger.logInfo("Downloading additional libs for Android ...");
        Path sdk = configuration.getAndroidSdkPath();
        Path libsLocation = sdk.resolve("tools").resolve("lib").resolve("java11");

        Files.createDirectories(libsLocation);
        for (String url : ANDROID_DEPS) {
            URL link = new URL(url);
            String filename = url.substring(url.lastIndexOf('/') + 1);
            FileOps.downloadFile(link, libsLocation.resolve(filename));
        }
        Logger.logInfo("Additional libs for Android downloaded successfully");
    }

    /**
     * Runs Android SDK's sdkmanager with specified arguments
     * @param args array of arguments to be passed to process
     * @throws IOException in case anything goes wrong.
     * @throws InterruptedException in case anything goes wrong.
     */
    private void androidSdkManager(String[] args) throws IOException, InterruptedException {
        Path sdk = configuration.getAndroidSdkPath();
        Path tools = sdk.resolve("tools");
        Path libs = tools.resolve("lib");
        Path additionalLibs = libs.resolve("java11");

        Path license = sdk.resolve("licenses").resolve("android-sdk-license");
        if (!Files.exists(license)) {
            Logger.logDebug("Adding Android key");
            Files.createDirectories(license.getParent());
            Files.write(license, ANDROID_KEY.getBytes());
        }

        String[] cliArgs = new String[] {
                Paths.get(configuration.getGraalPath().toString(), "bin", "java").toString(),
                "-Dcom.android.sdklib.toolsdir=" + tools,
                "-classpath", libs + "/*:" + additionalLibs + "/*",
                "com.android.sdklib.tool.sdkmanager.SdkManagerCli"
        };
        String[] sdkmanagerArgs = Stream.of(cliArgs, args)
                .flatMap(Stream::of)
                .toArray(String[]::new);

        Logger.logDebug("Running sdkmanager with: " + String.join(" ", sdkmanagerArgs));
        int result = ProcessRunner.executeWithFeedback("sdkmanager", sdkmanagerArgs);
        if (result != 0) {
            throw new IOException("Could not run the Android sdk manager");
        }
    }

    /**
     * Downloads Android NDK and build tools
     * @throws IOException in case anything goes wrong.
     * @throws InterruptedException in case anything goes wrong.
     */
    private void fetchFromSdkManager() throws IOException, InterruptedException {
        Logger.logInfo("Downloading Android NDK and toolchain. It may take several minutes depending on your bandwidth.");
        androidSdkManager(ANDROID_SDK_PACKAGES);
        Logger.logInfo("Android NDK and toolchain downloaded successfully");
    }
}
