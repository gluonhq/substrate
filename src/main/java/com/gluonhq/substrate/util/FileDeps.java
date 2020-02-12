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
package com.gluonhq.substrate.util;


import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.InternalProjectConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FileDeps {

    private static final String JAVA_STATIC_ZIP = "labs-staticjdk-${target}-gvm-${version}.zip";
    private static final String JAVA_STATIC_URL = "https://download2.gluonhq.com/substrate/staticjdk/";
    private static final String JAVAFX_STATIC_ZIP = "openjfx-${version}-${target}-static.zip";
    private static final String JAVAFX_STATIC_URL = "https://download2.gluonhq.com/substrate/javafxstaticsdk/";
    private static final String LLC_URL = "https://download2.gluonhq.com/substrate/llvm/";

    private static final List<String> JAVA_FILES = Arrays.asList(
            "libjava.a", "libnet.a", "libnio.a", "libzip.a", "libprefs.a"
    );

    private static final List<String> JAVAFX_FILES = Arrays.asList(
            "javafx.base.jar", "javafx.controls.jar", "javafx.graphics.jar",
            "javafx.fxml.jar", "javafx.media.jar", "javafx.web.jar",
            "libglass.a"
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

    private final InternalProjectConfiguration configuration;

    public FileDeps(InternalProjectConfiguration config) {
        this.configuration = Objects.requireNonNull(config);
    }

    /**
     * Returns the path to the Java SDK static libraries for this configuration. The path is cached on the provided
     * configuration. If no custom directory has been set in the project configuration and <code>useGraalPath</code>
     * is set to <code>true</code>, it will use the <code>lib</code> directory inside the configured Graal path. If
     * <code>useGraalPath</code> is set to <code>false</code>, a custom Java SDK will be retrieved.
     *
     * @param useGraalPath specifies if the default Java SDK path should resolve to the Graal installation dir
     * @return the location of the static libraries of the Java SDK for the arch-os for this configuration
     * @throws IOException in case anything goes wrong.
     */
    public Path getJavaSDKLibsPath(boolean useGraalPath) throws IOException {
        if (!configuration.useCustomJavaStaticLibs() && useGraalPath) {
            return configuration.getGraalPath().resolve("lib");
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
    public boolean setupDependencies() throws IOException {
        String target = configuration.getTargetTriplet().getOsArch();

        if (!Files.isDirectory(Constants.USER_SUBSTRATE_PATH)) {
            Files.createDirectories(Constants.USER_SUBSTRATE_PATH);
        }

        Path javaStaticLibs = configuration.getJavaStaticLibsPath();
        Path defaultJavaStaticPath = configuration.getDefaultJavaStaticPath();
        boolean customJavaLocation = configuration.useCustomJavaStaticLibs();

        boolean downloadJavaStatic = false, downloadJavaFXStatic = false, downloadAndroidSdk = false,
                downloadAndroidNdk = false, downloadAndroidAdditionalLibs = false;

        // Java Static
        Logger.logDebug("Processing JavaStatic dependencies at " + javaStaticLibs.toString());

        if (configuration.isUseJNI()) {
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
                if (JAVAFX_FILES.stream()
                        .map(s -> new File(path, s))
                        .anyMatch(f -> !f.exists())) {
                    Logger.logDebug("jar file not found");
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
                        Logger.logDebug("jar file has invalid hashcode");
                        downloadJavaFXStatic = true;
                    }
                }
            }
        }
        // Android
        if (target.startsWith("android")) {
            Path AndroidSdk = configuration.getAndroidSdkPath();
            Path AndroidNdk = configuration.getAndroidNdkPath();

            Path libsLocation = AndroidSdk.resolve("tools").resolve("lib").resolve("java11");

            if (!Files.exists(AndroidSdk)) {
                downloadAndroidSdk = true;
            } 

            if (!Files.exists(libsLocation)) {
                downloadAndroidAdditionalLibs = true;
            }

            if (!Files.exists(AndroidNdk)) {
                downloadAndroidNdk = true;
            }
        }
        try {
            if (downloadJavaStatic) {
                downloadJavaZip(target, Constants.USER_SUBSTRATE_PATH);
            }

            if (downloadJavaFXStatic) {
                downloadJavaFXZip(target, Constants.USER_SUBSTRATE_PATH);
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

        } catch (IOException e) {
            throw new RuntimeException("Error downloading zips: " + e.getMessage());
        }
        Logger.logDebug("Setup dependencies done");

        if (!Files.exists(javaStaticLibs)) {
            Logger.logSevere("Error: path " + javaStaticLibs + " doesn't exist");
            return false;
        }
        if (configuration.isUseJavaFX() && !Files.exists(configuration.getJavafxStaticLibsPath())) {
            Logger.logSevere("Error: path " + configuration.getJavafxStaticLibsPath() + " doesn't exist");
            return false;
        }
        return true;
    }

    /**
     * Returns the path to the llc compiler that is working for the provided configuration.
     * The <code>configuration</code> object must have its host triplet set correctly.
     * If the llc compiler is found in the file cache, it will be returned. Otherwise, it will
     * be downloaded and stored in the cache. After calling this method, it is guaranteed that an
     * llc compiler is in the specified path.
     * <p>
     *     There might be different versions for the llc compiler, but this is handled inside
     *     Substrate. If the developer wants a specific flavour of llc, it is recommended to
     *     use <code>configuration.setLlcPath()</code> which takes precedence over calling this method.
     * </p>
     * @return the path to a working llc compiler.
     * @throws IOException in case the required directories can't be created or navigated into.
     */
    public Path getLlcPath() throws IOException {
        Path llcRootPath = Constants.USER_SUBSTRATE_PATH.resolve(Constants.LLC_NAME);
        String archos = configuration.getHostTriplet().getArchOs();
        Path archosPath = llcRootPath.resolve(archos).resolve(Constants.LLC_VERSION);
        if (!Files.exists(archosPath)) {
            Files.createDirectories(archosPath);
        }
        String llcname = Constants.LLC_NAME + "-" + archos + "-" + Constants.LLC_VERSION;
        Path llcPath = archosPath.resolve(llcname);
        if (Files.exists(llcPath)) {
            return llcPath;
        }
        // we don't have the required llc. Download it and store it in llcPath.

        URL url = new URL(LLC_URL + llcname);
        FileOps.downloadFile(url, llcPath);
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(llcPath, perms);
        // now llcPath contains the llc, return it.
        return llcPath;
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

    private void downloadJavaZip(String target, Path substratePath) throws IOException {
        Logger.logDebug("Process zip javaStaticSdk, target = "+target);

        String javaZip = Strings.substitute(JAVA_STATIC_ZIP, Map.of(
            "version", configuration.getJavaStaticSdkVersion(),
            "target", target));
        FileOps.downloadAndUnzip(JAVA_STATIC_URL + javaZip,
                substratePath,
                javaZip,
                "javaStaticSdk",
                configuration.getJavaStaticSdkVersion(),
                configuration.getTargetTriplet().getOsArch());

        Logger.logDebug("Processing zip java done");
    }

    private void downloadJavaFXZip(String osarch, Path substratePath ) throws IOException {
        Logger.logDebug("Process zip javafxStaticSdk");

        String javafxZip = Strings.substitute(JAVAFX_STATIC_ZIP, Map.of(
            "version", configuration.getJavafxStaticSdkVersion(),
            "target", osarch));
        FileOps.downloadAndUnzip(JAVAFX_STATIC_URL + javafxZip,
                substratePath,
                javafxZip,
                "javafxStaticSdk",
                configuration.getJavafxStaticSdkVersion(),
                configuration.getTargetTriplet().getOsArch());

        Logger.logDebug("Process zip javafx done");
    }

    private void downloadAndroidSdkZip() throws IOException {
        Path sdk = configuration.getAndroidSdkPath();
        String hostOs = configuration.getHostTriplet().getOs();
        String androidSdkUrl = Strings.substitute(ANDROID_SDK_URL, Map.of("host", hostOs));
        System.out.println("Downloading Android SDK...");
        FileOps.downloadAndUnzip(androidSdkUrl, sdk.getParent(), "android-sdk.zip", sdk.getFileName().toString(), ".");
        System.out.println("Done");
    }

    private void downloadAdditionalAndroidLibs() throws IOException {
        Path sdk = configuration.getAndroidSdkPath();
        Path libsLocation = sdk.resolve("tools").resolve("lib").resolve("java11");

        Files.createDirectories(libsLocation);
        System.out.println("Downloading additional libs ...");
        for (String url : ANDROID_DEPS) {
            URL link = new URL(url);
            String filename = url.substring(url.lastIndexOf('/')+1, url.length());
            FileOps.downloadFile(link, libsLocation.resolve(filename));
        }
        System.out.println("Done");
    }

    private void androidSdkManager(String[] args) {
        Path sdk = configuration.getAndroidSdkPath();
        Path tools = sdk.resolve("tools");
        Path libs = tools.resolve("lib");
        Path additionalLibs = libs.resolve("java11");

        ProcessRunner sdkmanager = new ProcessRunner("java", "-Dcom.android.sdklib.toolsdir=" + tools, "-classpath",
                libs + "/*:" + additionalLibs + "/*", "com.android.sdklib.tool.sdkmanager.SdkManagerCli");
        sdkmanager.addArgs(args);
        sdkmanager.setInteractive(true);

        System.out.println("Running sdkmanager with: " + sdkmanager.getCmd());
        try {
            sdkmanager.runProcess("sdkmanager");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void fetchFromSdkManager() {
        System.out.println("Downloading Android toolchain...");
        String[] args = {"platforms;android-27", "build-tools;27.0.3", "platform-tools", "extras;android;m2repository", "extras;google;m2repository", "ndk-bundle"};
        androidSdkManager(args);
        System.out.println("Done");
    }
} 