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

import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.gluonhq.substrate.util.XcodeUtils.XCODE_PRODUCTS_PATH;

public class Deploy {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String LIBIMOBILEDEVICE = "libimobiledevice-1.0";
    private static final List<String> LIBIMOBILEDEVICE_DEPENDENCIES = Arrays.asList(
            "libssl", "libcrypto", "libusbmuxd-2.0", "libplist-2.0");

    private Path iosDeployPath;

    public Deploy() throws IOException, InterruptedException {
        checkPrerequisites();
    }

    public Path getIosDeployPath() {
        return iosDeployPath;
    }

    private void checkPrerequisites() throws IOException, InterruptedException {
        iosDeployPath = null;

        // Check for Homebrew installed
        String response = ProcessRunner.runProcessForSingleOutput("check brew","which", "brew");
        if (response == null || response.isEmpty() || !Files.exists(Path.of(response))) {
            Logger.logSevere("Homebrew not found");
            throw new RuntimeException("Open a terminal and run the following command to install Homebrew: \n\n" +
                    "ruby -e \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)\"");
        }
        Logger.logDebug("Brew found at " + response);

        // Check if dependencies of libimobiledevice are installed and retrieve linked versions
        Map<String, List<String>> map = new HashMap<>();
        for (String nameLib : LIBIMOBILEDEVICE_DEPENDENCIES) {
            List<String> pathLibs = getDependencyPaths(nameLib);
            List<String> linkLibs = checkDependencyLinks(nameLib, pathLibs);
            map.put(nameLib, linkLibs);
        }

        // Check for libimobiledevice installed
        List<String> libiPath = getDependencyPaths(LIBIMOBILEDEVICE);
        ProcessRunner runner = new ProcessRunner("otool", "-L", libiPath.get(0));
        if (runner.runProcess("otool") == 0) {
            for (String key : map.keySet()) {
                if (runner.getResponses().stream()
                        .noneMatch(link -> map.get(key).stream().anyMatch(link::contains))) {
                    Logger.logSevere("Error: there is a mismatch in the dependency (" + key + ") required by libimobiledevice.dylib: " + map.get(key) + "is required but it wasn't found");
                    throw new RuntimeException("Open a terminal and run the following command to reinstall the required libraries: \n\n" +
                            "brew reinstall " + key);
                }
            }
        }

        // Check for ios-deploy installed
        response = ProcessRunner.runProcessForSingleOutput("check ios-deploy","which", "ios-deploy");
        if (response == null || response.isEmpty() || !Files.exists(Path.of(response))) {
            if (installIOSDeploy()) {
                checkPrerequisites();
            }
        } else {
            // Check for ios-deploy version installed (it should be 1.10+)
            String version = ProcessRunner.runProcessForSingleOutput("ios-deploy version","ios-deploy", "-V");
            if (version != null && !version.isEmpty() && (version.startsWith("1.8") || version.startsWith("1.9"))) {
                Logger.logDebug("ios-deploy version (" + version + ") is outdated");
                uninstallIOSDeploy();
                if (installIOSDeploy()) {
                    checkPrerequisites();
                }
            } else {
                Logger.logDebug("ios-deploy found at " + response);
                iosDeployPath = Path.of(response);
            }
        }
    }

    private String[] connectedDevices() throws IOException, InterruptedException {
        if (iosDeployPath == null) {
            return new String[] {};
        }
        ProcessRunner runner = new ProcessRunner("ios-deploy", "-c");
        if (!runner.runTimedProcess("connected devices", 10L)) {
            Logger.logSevere("Error finding connected devices");
            return new String[] {};
        }
        List<String> devices = runner.getResponses();
        return devices.stream()
                .filter(line -> line.startsWith("[....] Found"))
                .map(line -> line.substring("[....] Found ".length()).split("\\s")[0])
                .peek(id -> Logger.logDebug("ID found: " + id))
                .toArray(String[]::new);
    }

    public boolean install(String app) throws IOException, InterruptedException {
        if (iosDeployPath == null) {
            Logger.logSevere("Error: ios-deploy was not found");
            return false;
        }

        String[] devices = connectedDevices();
        if (devices == null || devices.length == 0) {
            Logger.logInfo("No iOS devices connected to this system. Exit install procedure");
            return false;
        }
        if (devices.length > 1) {
            Logger.logInfo("Multiple iOS devices connected to this system: " + String.join(", ", devices ) + ". We'll use the first one.");
        }
        String deviceId = devices[0];

        ProcessRunner trustRunner = new ProcessRunner("ios-deploy", "-C");
        trustRunner.showSevereMessage(false);
        if (trustRunner.runProcess("trusted computer") != 0) {
            Logger.logInfo("\n\nComputer not trusted!\nPlease, unplug and plug again your phone, and trust your computer when the dialog shows up on your device.\nThen try again");
            return false;
        }

        ProcessRunner runner = new ProcessRunner(iosDeployPath.toString(),
                "--id", deviceId, "--bundle", app, "--no-wifi", "--debug", "--noninteractive");
        runner.addToEnv("PATH", "/usr/bin/:$PATH");
        runner.setInfo(true);
        boolean keepTrying = true;
        while (keepTrying) {
            keepTrying = false;
            boolean result = runner.runTimedProcess("run", 60);
            Logger.logInfo("result = " + result);
            if (result) {
                if (runner.getResponses().stream().anyMatch("Error: The device is locked."::equals)) {
                    Logger.logInfo("\n\nDevice locked!\nPlease, unlock and press ENTER to try again");
                    System.in.read();
                    keepTrying = true;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public void addDebugSymbolInfo(Path appPath, String appName) throws IOException, InterruptedException {
        Path applicationPath = appPath.resolve(appName + ".app");
        Path debugSymbolsPath = Path.of(applicationPath.toString() + ".dSYM");
        if (Files.exists(debugSymbolsPath)) {
            FileOps.deleteDirectory(debugSymbolsPath);
        }
        Path executablePath = applicationPath.resolve(appName);

        Logger.logDebug("Generating debug symbol files...");
        ProcessRunner runner = new ProcessRunner("xcrun", "dsymutil", "-o", debugSymbolsPath.toString(), executablePath.toString());
        if (runner.runProcess("dsymutil") == 0) {
            copyAppToProducts(debugSymbolsPath, executablePath, appName);
        } else {
            throw new RuntimeException("Error generating debug symbol files");
        }
    }

    private void copyAppToProducts(Path debugSymbolsPath, Path executablePath, String appName) throws IOException {
        if (Files.exists(XCODE_PRODUCTS_PATH)) {
            List<Path> oldAppsPaths = Files.walk(XCODE_PRODUCTS_PATH, 1)
                    .filter(Objects::nonNull)
                    .filter(path -> path.getFileName().toString().startsWith(appName))
                    .collect(Collectors.toList());
            for (Path path : oldAppsPaths) {
                Logger.logDebug("Removing older version: " + path.getFileName().toString());
                FileOps.deleteDirectory(path);
            }
        }

        String now = DATE_TIME_FORMATTER.format(LocalDateTime.now());
        Path productAppPath = XCODE_PRODUCTS_PATH.resolve(appName + "_" + now);
        Files.createDirectories(productAppPath);

        Path productExecAppPath = productAppPath.resolve(appName + ".app");
        Files.createDirectories(productExecAppPath);
        Files.copy(executablePath, productExecAppPath.resolve(executablePath.getFileName()));

        Path productDebugSymbolsPath = productAppPath.resolve(debugSymbolsPath.getFileName());
        Files.createDirectories(productDebugSymbolsPath);
        FileOps.copyDirectory(debugSymbolsPath, productDebugSymbolsPath);
    }

    /**
     * Returns a list with one or more valid paths with the existing native libraries for the given
     * name. If no paths are found, an IOException is thrown, asking the user to install it
     * manually from command line
     *
     * @param nameLib the name of the library
     * @return a non-empty list with paths to native libraries
     * @throws IOException
     * @throws InterruptedException
     */
    private List<String> getDependencyPaths(String nameLib) throws IOException, InterruptedException {
        ProcessRunner runner = new ProcessRunner("/bin/sh", "-c", "find $(brew --cellar) -name " + nameLib + ".dylib");
        if (runner.runProcess(nameLib) != 0) {
            throw new IOException("Error finding " + nameLib);
        }

        List<String> list = runner.getResponses().stream()
                .filter(libPath -> libPath != null && !libPath.isEmpty() && Files.exists(Path.of(libPath)))
                .peek(libPath -> Logger.logDebug("lib " + nameLib + " found at " + libPath))
                .collect(Collectors.toList());

        if (list.isEmpty()) {
            if (nameLib.contains("-")) {
                Logger.logDebug("Trying old version of " + nameLib + ".dylib");
                return getDependencyPaths(nameLib.split("-")[0]);
            } else {
                Logger.logSevere("Error: " + nameLib + ".dylib was not found");
                throw new IOException("Open a terminal and run the following command to install " + nameLib + ": \n\n" +
                            "brew install --HEAD " + nameLib);
            }
        }
        return list;
    }

    private List<String> checkDependencyLinks(String nameLib, List<String> libPaths) throws IOException, InterruptedException {
        List<String> libLinks = new ArrayList<>();
        for (String libPath : libPaths) {
            // retrieve name of linked library
            String linkedLib = ProcessRunner.runProcessForSingleOutput("readlink " + nameLib, "readlink", libPath);
            Logger.logDebug(nameLib + ".dylib link of: " + linkedLib);
            if (linkedLib == null || linkedLib.isEmpty()) {
                throw new RuntimeException("Error finding " + nameLib + ".dylib version");
            }
            libLinks.add(linkedLib);
        }
        return libLinks;
    }

    private boolean uninstallIOSDeploy() throws IOException, InterruptedException {
        ProcessRunner runner = new ProcessRunner("brew", "unlink", "ios-deploy");
        if (runner.runProcess("ios-deploy unlink") == 0) {
            Logger.logDebug("ios-deploy unlinked");
        }
        Logger.logDebug("Uninstalling ios-deploy");
        runner = new ProcessRunner("brew", "uninstall", "ios-deploy");
        if (runner.runProcess("ios-deploy uninstall") == 0) {
            Logger.logDebug("ios-deploy uninstalled");
            return true;
        }
        return false;
    }

    private boolean installIOSDeploy() throws IOException, InterruptedException {
        Logger.logInfo("ios-deploy not found. It will be installed now");
        Path tmpDeploy = FileOps.copyResourceToTmp("/thirdparty/ios-deploy/ios-deploy.rb");

        ProcessRunner runner = new ProcessRunner("brew", "install", "--HEAD", tmpDeploy.toString());
        if (runner.runProcess("ios-deploy") == 0) {
            Logger.logDebug("ios-deploy installed");
            return true;
        }
        throw new RuntimeException("Error installing ios-deploy. See detailed message above on how to proceed. Then try to deploy again");
    }
}
