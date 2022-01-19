/*
 * Copyright (c) 2022, Gluon
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

import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Simulator {

    // https://suelan.github.io/2020/02/05/iOS-Simulator-from-the-Command-Line/
    private static final Pattern DEVICE_PATTERN = Pattern.compile("^(.+)\\(([0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12})\\)\\s(.+)");
    private static final String SIM_APP_PATH = "/Applications/Xcode.app/Contents/Developer/Applications/Simulator.app/";

    private final ProcessPaths paths;
    private final InternalProjectConfiguration projectConfiguration;
    private final String bundleId;

    public Simulator(ProcessPaths paths, InternalProjectConfiguration projectConfiguration) {
        this.paths = paths;
        this.projectConfiguration = projectConfiguration;
        String sourceOS = projectConfiguration.getTargetTriplet().getOs();
        this.bundleId = InfoPlist.getBundleId(InfoPlist.getPlistPath(paths, sourceOS), sourceOS);
    }

    /**
     * Finds a valid simulator device, that can be set from
     * the user or a default one and installs the app
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean installApp() throws IOException, InterruptedException {
        String deviceName = projectConfiguration.getReleaseConfiguration().getSimulatorDevice();
        Logger.logDebug("Preparing SimDevice:  " + deviceName);
        // get one valid booted device
        SimDevice simDevice = bootDevice(deviceName);
        // install app
        ProcessRunner runner = new ProcessRunner("xcrun", "simctl", "install", simDevice.getId(),
                paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app").toString());
        if (runner.runProcess("install") != 0) {
            throw new IOException("Error installing app in simulator");
        }
        Logger.logInfo("App successfully installed in simulator device: " + deviceName);
        return true;
    }

    /**
     * Finds a valid simulator device, that can be set from
     * the user or a default one, launches the Simulator application,
     * and launches the app on it
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void launchSimulator() throws IOException, InterruptedException {
        String deviceName = projectConfiguration.getReleaseConfiguration().getSimulatorDevice();
        Logger.logDebug("Preparing SimDevice:  " + deviceName);
        // get one valid booted device
        SimDevice simDevice = bootDevice(deviceName);
        // check app is installed
        validateBundleId(bundleId);
        // open Simulator
        openSimulator(simDevice);
        // launch app, output goes to console
        Logger.logInfo("Launching app on SimDevice...");
        ProcessRunner runner = new ProcessRunner("xcrun", "simctl", "launch", "--console-pty", simDevice.getId(), bundleId);
        runner.setInfo(true);
        if (runner.runProcess("launch") != 0) {
            throw new IOException("Error launching app in simulator");
        }
    }

    private static void openSimulator(SimDevice simDevice) throws IOException, InterruptedException {
        ProcessRunner runner = new ProcessRunner("open", SIM_APP_PATH, "--args",
                "-CurrentDeviceUDID", simDevice.getId());
        if (runner.runProcess("open sim") != 0) {
            throw new IOException("Error opening simulator");
        }
    }

    private static SimDevice bootDevice(String deviceName) throws IOException, InterruptedException {
        SimDevice device = getBootedSimDevice(deviceName);
        if (device == null) {
            throw new IOException("Error: SimDevice was null");
        }
        return device;
    }

    private static SimDevice getBootedSimDevice(String deviceName) throws IOException, InterruptedException {
        List<SimDevice> devices = getSimDevices();
        SimDevice device;
        if (deviceName != null) {
            // If user has set a device name, check that is available
            device = devices.stream()
                    .filter(d -> deviceName.equals(d.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IOException("No device found matching " + deviceName +
                            "\nPossible devices are:\n " +
                            devices.stream().map(SimDevice::getName).collect(Collectors.joining("\n"))));
        } else {
            // deviceName was not set, find first booted device
            device = devices.stream()
                    .filter(d -> d.getState().contains("Booted"))
                    .findFirst()
                    .orElse(null);
            if (device == null) {
                // else default to iPhone 13
                device = devices.stream()
                        .filter(d -> "iPhone 13".equals(d.getName()))
                        .findFirst()
                        .orElseThrow(() -> new IOException("No device found. " +
                                "\nPossible devices are:\n " +
                                devices.stream().map(SimDevice::getName).collect(Collectors.joining("\n"))));
            }
        }
        Logger.logDebug("Found SimDevice: " + device);
        if (device != null && !device.getState().contains("Booted")) {
            // once we have a device, boot it if it isn't booted yet
            Logger.logDebug("Booting SimDevice: " + device);
            ProcessRunner runner = new ProcessRunner("xcrun", "simctl", "boot", device.getId());
            runner.setInfo(true);
            if (runner.runProcess("boot") != 0) {
                throw new IOException("Error booting " + device);
            }
        }
        return device;
    }

    private static List<SimDevice> getSimDevices() throws IOException, InterruptedException {
        List<SimDevice> devices = null;
        ProcessRunner runner = new ProcessRunner("xcrun", "simctl", "list", "devices");
        if (runner.runProcess("sim") == 0) {
            devices = runner.getResponses().stream()
                    .map(line -> DEVICE_PATTERN.matcher(line.trim()))
                    .filter(Matcher::find)
                    .map(matcher -> new SimDevice(normalize(matcher.group(1).trim()),
                            matcher.group(2), matcher.group(4)))
                    .filter(d -> !d.getState().contains("unavailable"))
                    .collect(Collectors.toList());
            Logger.logDebug("Number of simulator devices found: " + devices.size());
            Logger.logDebug("List of simulator devices found: " + devices.size() + "\n" +
                    devices.stream().map(SimDevice::toString).collect(Collectors.joining("\n")));
        }
        return devices;
    }

    private static void validateBundleId(String bundleId) throws IOException, InterruptedException {
        ProcessRunner runner = new ProcessRunner("/bin/sh", "-c",
                "xcrun simctl listapps booted | plutil -convert json - -o - | ruby -r json -e 'puts JSON.parse(STDIN.read).keys'");
        if (runner.runProcess("get installed apps") != 0) {
            throw new IOException("Error finding installed apps from booted simulator");
        }
        if (runner.getResponses().stream().noneMatch(bundleId::equals)) {
            throw new IOException("Booted simulator doesn't have an app with bundle id: " + bundleId + "\n" +
                    "Make sure you install it first.");
        }
    }

    // remove small caps
    private static String normalize(String text) {
        return text.replace('\u0280', 'R');
    }

    private static class SimDevice {

        private final String name;
        private final String id;
        private final String state;

        SimDevice(String name, String id, String state) {
            this.name = name;
            this.id = id;
            this.state = state;
        }

        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }

        public String getState() {
            return state;
        }

        @Override
        public String toString() {
            return "SimDevice{" +
                    "name='" + name + '\'' +
                    ", id='" + id + '\'' +
                    ", state='" + state + '\'' +
                    '}';
        }
    }
}
