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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.gluonhq.substrate.util.XcodeUtils.XCODE_PRODUCTS_PATH;

public class Deploy {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private String usbLib;

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

        // Check for usbmuxd installed
        String usbPath = ProcessRunner.runProcessForSingleOutput("libusbmuxd","/bin/sh", "-c", "find $(brew --cellar) -name libusbmuxd.dylib");
        if (usbPath == null || usbPath.isEmpty() || !Files.exists(Path.of(usbPath))) {
            Logger.logSevere("Error finding libusbmuxd.dylib");
            throw new RuntimeException("Open a terminal and run the following command to install libusbmuxd.dylib: \n\n" +
                    "brew install --HEAD usbmuxd");
        }
        Logger.logDebug("libusbmuxd.dylib found in: " + usbPath);

        // retrieve usbmuxd linked library
        usbLib = ProcessRunner.runProcessForSingleOutput("readlink libusbmuxd", "readlink", usbPath);
        Logger.logDebug("libusbmuxd.dylib link of: " + usbLib);
        if (usbLib == null || usbLib.isEmpty()) {
            throw new RuntimeException("Error finding libusbmuxd.dylib version");
        }

        // Check for libimobiledevice installed
        String libiPath = ProcessRunner.runProcessForSingleOutput("libimobiledevice","/bin/sh", "-c", "find $(brew --cellar) -name libimobiledevice.dylib");
        if (libiPath == null || libiPath.isEmpty() || !Files.exists(Path.of(libiPath))) {
            Logger.logSevere("Error finding libimobiledevice.dylib");
            throw new RuntimeException("Open a terminal and run the following command to install libimobiledevice.dylib: \n\n" +
                    "brew install --HEAD libimobiledevice");
        }
        Logger.logDebug("libimobiledevice.dylib found in: " + libiPath);

        ProcessRunner runner = new ProcessRunner("otool", "-L", libiPath);
        if (runner.runProcess("otool") == 0) {
            if (runner.getResponses().stream().noneMatch(d -> d.contains(usbLib))) {
                Logger.logSevere("Error: there is a mismatch in the dependencies required by libimobiledevice.dylib");
                throw new RuntimeException("Open a terminal and run the following command to reinstall the required libraries: \n\n" +
                        "brew reinstall usbmuxd & brew reinstall libimobiledevice");
            }
        }

        Logger.logInfo("Loading libimobiledevice.dylib ...");

        // Check for ios-deploy installed
        response = ProcessRunner.runProcessForSingleOutput("check ios-deploy","which", "ios-deploy");
        if (response == null || response.isEmpty() || !Files.exists(Path.of(response))) {
            Logger.logSevere("ios-deploy not found. It will be installed now");
            runner = new ProcessRunner("brew", "install", "ios-deploy");
            if (runner.runProcess("ios-deploy") == 0) {
                Logger.logDebug("ios-deploy installed");
                checkPrerequisites();
            } else {
                Logger.logDebug("Error installing ios-deploy");
                return;
            }
        }

        Logger.logDebug("ios-deploy found at " + response);
        iosDeployPath = Path.of(response);
    }

    public String[] connectedDevices() throws IOException, InterruptedException {
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
                .toArray(String[]::new);
    }

    public boolean install(String app) throws IOException, InterruptedException {
        if (iosDeployPath == null) {
            Logger.logSevere("Error: ios-deploy was not found");
            return false;
        }

        String[] devices = connectedDevices();
        if (devices == null || devices.length == 0) {
            Logger.logSevere("No iOS devices connected to this system. Exit install procedure");
            return false;
        }
        if (devices.length > 1) {
            Logger.logSevere("Multiple iOS devices connected to this system: " + String.join(", ", devices ) + ". We'll use the first one.");
        }
        String deviceId = devices[0];

        ProcessRunner runner = new ProcessRunner(iosDeployPath.toString(),
                "--id", deviceId, "--bundle", app, "--no-wifi", "--debug", "--noninteractive");
        runner.addToEnv("PATH", "/usr/bin/:$PATH");
        runner.setInfo(true);
        boolean result = runner.runTimedProcess("run", 60);
        Logger.logInfo("result = " + result);
        return result;
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
}
