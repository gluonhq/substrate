/*
 * Copyright (c) 2019, 2026, Gluon
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.gluonhq.substrate.util.XcodeUtils.XCODE_PRODUCTS_PATH;

/**
 * Installs and runs an iOS app on a connected device, using {@code devicectl},
 * the tool that ships with Xcode 15 and later.
 * <p>This replaces ios-deploy, which cannot launch apps on iOS 17 or later, and with
 * it the Homebrew and libimobiledevice prerequisites it needed.
 */
public class Deploy {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Selects the devices that an app can be deployed to: real iOS devices that are
     * reachable.
     *<p>Only devices on a wired transport are considered.
     *<p>A device alternates between the "connected" and "available (paired)" states as
     * its tunnel comes and goes, and devicectl establishes one on demand, so both are accepted.
     * <p>The clauses are column titles from the table output. The Reality clause excludes booted simulators.
     */
    private static final String DEVICE_FILTER =
            "Platform = 'iOS' AND Reality = 'physical'" +
            " AND properties.connection.transportType = 'wired'" +
            " AND (State = 'connected' OR State BEGINSWITH 'available')";

    private static final String CONNECTED_STATE = "connected";

    /**
     * Exit code devicectl uses when it is interrupted, printing "User cancelled".
     * Pressing Ctrl+C during a --console run is the expected way to stop the app, so
     * it is not a failure, unlike the exit code 1 used when a launch actually fails.
     */
    private static final int DEVICECTL_USER_CANCELLED = 3;

    /** How long a launch waits for a locked device to be unlocked, and how often it looks. */
    private static final long UNLOCK_TIMEOUT_MILLIS = 60_000L;
    private static final long UNLOCK_POLL_MILLIS = 2_000L;

    /**
     * A row of "devicectl list devices", as "&lt;identifier&gt; (&lt;kind&gt;)   &lt;state&gt;". The kind
     * annotation is always present, so requiring it rejects the "No matching devices
     * found." line that devicectl prints, with a zero exit code, when nothing matches.
     */
    private static final Pattern DEVICE_ROW = Pattern.compile("^(\\S+)\\s+\\([^)]+\\)\\s*(.*)$");

    private Path devicectlPath;

    public Deploy() throws IOException, InterruptedException {
        checkPrerequisites();
    }

    /**
     * Adds debug symbols into a .dSYM bundle
     *
     * @param appPath the path of the app bundle
     * @param appName the name of the app
     * @throws IOException
     * @throws InterruptedException
     */
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

    /**
     * Installs the .app bundle on a connected iOS device, but it doesn't launch it
     *
     * @param app The path of the .app bundle
     * @return True if the process succeeds
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean install(String app) throws IOException, InterruptedException {
        String deviceId = prepareDeploy();
        if (deviceId == null) {
            return false;
        }

        long deadline = System.currentTimeMillis() + UNLOCK_TIMEOUT_MILLIS;
        while (true) {
            ProcessRunner runner = new ProcessRunner("xcrun", "devicectl", "device", "install", "app",
                    "--device", deviceId, app);
            runner.setInfo(true);
            runner.showSevereMessage(false);
            if (runner.runProcess("install app") == 0) {
                Logger.logDebug("The app: " + app + " was installed successfully");
                return true;
            }

            // Installing onto a locked device normally succeeds, but after boot the device needs the passcode.
            if (System.currentTimeMillis() < deadline && isLocked(deviceId)) {
                if (waitForUnlock(deviceId, deadline)) {
                    continue;
                }
                Logger.logInfo("Installing " + app + " failed: the device was still locked after " +
                        (UNLOCK_TIMEOUT_MILLIS / 1000) + " seconds");
                return false;
            }
            Logger.logInfo("There was an error installing the app " + app);
            return false;
        }
    }

    /**
     * Runs an app on a connected iOS device, providing that is already installed,
     * and streams its console output until the app terminates.
     *
     * @param app The path of the .app bundle
     * @param bundleID The bundle id of the installed app
     * @return True if the process succeeds
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean run(String app, String bundleID) throws IOException, InterruptedException {
        String deviceId = prepareDeploy();
        if (deviceId == null) {
            return false;
        }

        Boolean installed = isInstalled(deviceId, bundleID);
        if (installed == null) {
            Logger.logInfo("\n\nThe device " + deviceId + " could not be reached.\n" +
                    "Please, make sure it is connected and unlocked, and then try again");
            return false;
        }
        if (!installed) {
            Logger.logInfo("\n\nThe bundle id " + bundleID + " is not found on the device.\nPlease, install it first, and then try again");
            return false;
        }

        Logger.logInfo("Launching " + bundleID + ". The output of the app is shown below, press Ctrl+C to stop it.\n");
        long deadline = System.currentTimeMillis() + UNLOCK_TIMEOUT_MILLIS;
        while (true) {
            // SpringBoard refuses to open an app while the device is locked, so wait for the lock screen to go away
            boolean unlocked = waitForUnlock(deviceId, deadline);

            ProcessRunner runner = new ProcessRunner("xcrun", "devicectl", "device", "process", "launch",
                    "--console", "--terminate-existing", "--device", deviceId, bundleID);
            // --console connects the standard streams of the app to those of devicectl and
            // forwards catchable signals to it, so let devicectl inherit this terminal and
            // wait for the app to terminate.
            runner.setInteractive(true);
            runner.showSevereMessage(false);
            int result = runner.runProcess("launch app");
            if (result == DEVICECTL_USER_CANCELLED) {
                Logger.logDebug("Run of " + bundleID + " was cancelled");
                return true;
            }
            if (result == 0) {
                Logger.logDebug("The app: " + app + " with bundle id: " + bundleID + " has terminated");
                return true;
            }
            // Only a locked device is worth another attempt
            if (System.currentTimeMillis() < deadline && isLocked(deviceId)) {
                continue;
            }
            // devicectl has already printed why, on the terminal it inherited
            if (!unlocked) {
                Logger.logInfo("\n\nThe app " + bundleID + " failed to launch: the device was still " +
                        "locked after " + (UNLOCK_TIMEOUT_MILLIS / 1000) + " seconds.\n" +
                        "Unlock it and run the command again");
            } else {
                Logger.logInfo("\n\nThe app " + bundleID + " failed to launch.\n" +
                        "The error reported by devicectl is shown above");
            }
            return false;
        }
    }

    /**
     * For tests only
     * @return the path of devicectl
     */
    public Path getDevicectlPath() {
        return devicectlPath;
    }

    // private

    /**
     * Verifies that devicectl is available. It is installed as part of Xcode 15 and later
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private void checkPrerequisites() throws IOException, InterruptedException {
        devicectlPath = null;
        String response = ProcessRunner.runProcessForSingleOutput("check devicectl", "xcrun", "-f", "devicectl");
        if (response == null || response.isEmpty() || !Files.exists(Path.of(response))) {
            Logger.logSevere("devicectl not found. It is part of Xcode 15 and later: " +
                    "please install or update Xcode, and make sure the command line tools are selected " +
                    "(xcode-select -p)");
            return;
        }
        devicectlPath = Path.of(response);
        Logger.logDebug("devicectl found at " + devicectlPath);
    }

    /**
     * Blocks while the device is locked, until it is unlocked or the deadline passes.
     *
     * @param deviceId the id of the device
     * @param deadline the moment, in milliseconds, to give up at
     * @return true if the device is unlocked, false if the deadline passed first
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean waitForUnlock(String deviceId, long deadline) throws IOException, InterruptedException {
        boolean reported = false;
        while (isLocked(deviceId)) {
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            if (!reported) {
                Logger.logInfo("The device is locked. Waiting up to " + (UNLOCK_TIMEOUT_MILLIS / 1000) +
                        " seconds for it to be unlocked, press Ctrl+C to give up");
                reported = true;
            }
            Thread.sleep(UNLOCK_POLL_MILLIS);
        }
        return true;
    }

    /**
     * Asks the device whether it currently requires its passcode, which is what makes
     * SpringBoard refuse to open an app.
     *
     * @param deviceId the id of the device
     * @return true if the device is locked
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean isLocked(String deviceId) throws IOException, InterruptedException {
        ProcessRunner runner = new ProcessRunner("xcrun", "devicectl", "device", "info", "lockState",
                "--device", deviceId, "--timeout", "20");
        runner.showSevereMessage(false);
        if (runner.runProcess("lock state") != 0) {
            return false;
        }
        return runner.getResponses().stream()
                .anyMatch(line -> line.contains("passcodeRequired") && line.contains("true"));
    }

    /**
     * Checks if an app with a given bundle id is installed on a given device.
     * <p>devicectl exits successfully whether the app is there, listing the bundle
     * id only when it is installed.
     *
     * @param deviceId the id of the connected device
     * @param bundleID the bundle id of the app
     * @return true if installed, false if not, or null if the device was unreachable
     * @throws IOException
     * @throws InterruptedException
     */
    private Boolean isInstalled(String deviceId, String bundleID) throws IOException, InterruptedException {
        ProcessRunner runner = new ProcessRunner("xcrun", "devicectl", "device", "info", "apps",
                "--device", deviceId, "--bundle-id", bundleID, "--timeout", "30",
                "--hide-headers", "--hide-default-columns", "--columns", "Bundle Identifier");
        runner.showSevereMessage(false);
        if (runner.runProcess("app installed") != 0) {
            return null;
        }
        return runner.getResponses().stream()
                .map(String::trim)
                .anyMatch(bundleID::equals);
    }

    /**
     * Copies the app and the debug symbols to the Xcode products path, so
     * Xcode and Instruments can symbolicate the app
     *
     * @param debugSymbolsPath path of the dSYM bundle
     * @param executablePath path of executable
     * @param appName the app name
     * @throws IOException
     */
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
     * Returns the device id of the first connected device to the computer.
     *
     * @return The device id of the connected device, or null if devicectl is missing
     * @throws IOException
     * @throws InterruptedException
     */
    private String prepareDeploy() throws IOException, InterruptedException {
        if (devicectlPath == null) {
            Logger.logSevere("Error: devicectl was not found");
            return null;
        }
        return getFirstConnectedDevice()
                .orElseThrow(() -> new IOException("No iOS devices connected to this system"));
    }

    /**
     * Retrieves the iOS device connected to the computer, if any. In case there are
     * multiple devices, the first one will be used
     *
     * @return An optional with the id of the first connected device, or empty if not found
     * @throws IOException
     * @throws InterruptedException
     */
    private Optional<String> getFirstConnectedDevice() throws IOException, InterruptedException {
        List<String> devices = connectedDevices();
        if (devices == null || devices.isEmpty()) {
            return Optional.empty();
        }
        if (devices.size() > 1) {
            Logger.logInfo("Multiple iOS devices connected to this system: " + String.join(", ", devices) + ".\nThe first one will be used.");
        }
        return Optional.of(devices.get(0));
    }

    /**
     * Retrieves a list of all real iOS devices that are connected to the computer,
     * ignoring simulators and devices that are merely paired.
     *
     * @return List of all connected devices to the computer
     * @throws IOException
     * @throws InterruptedException
     */
    private List<String> connectedDevices() throws IOException, InterruptedException {
        if (devicectlPath == null) {
            Logger.logSevere("Error: devicectl was not found");
            return null;
        }

        ProcessRunner runner = new ProcessRunner("xcrun", "devicectl", "list", "devices",
                "--filter", DEVICE_FILTER,
                "--hide-headers", "--hide-default-columns", "--columns", "Identifier", "--columns", "State");
        if (!runner.runTimedProcess("connected devices", 30L)) {
            Logger.logSevere("Error finding connected devices");
            return List.of();
        }

        // Rows are "<identifier> (<kind>)   <state>", the state being either "connected" or "available (paired)".
        List<String> connected = new ArrayList<>();
        List<String> paired = new ArrayList<>();
        for (String response : runner.getResponses()) {
            String row = response.trim();
            if (row.isEmpty()) {
                continue;
            }
            Matcher matcher = DEVICE_ROW.matcher(row);
            if (!matcher.matches()) {
                Logger.logDebug("Ignoring line that is not a device: " + row);
                continue;
            }
            String id = matcher.group(1);
            String state = matcher.group(2).trim();
            if (CONNECTED_STATE.equals(state)) {
                connected.add(id);
            } else {
                paired.add(id);
            }
            Logger.logDebug("ID found: " + id + " (" + state + ")");
        }

        if (!connected.isEmpty()) {
            Logger.logDebug("Number of iOS devices connected found: " + connected.size());
            return connected;
        }
        if (!paired.isEmpty()) {
            // The usual state for an idle device
            Logger.logDebug("No iOS device has an active connection, using paired device " + paired.get(0));
        }
        return paired;
    }
}
