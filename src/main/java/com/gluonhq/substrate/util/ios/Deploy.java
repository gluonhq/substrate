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
import com.gluonhq.substrate.util.ProcessRunner;

import java.io.IOException;
import java.nio.file.Path;

public class Deploy {

    private static MobileDeviceBridge bridge;

    public static Path getIOSDeployPath() throws IOException, InterruptedException {
        // Check for Homebrew installed
        String response = ProcessRunner.runProcessForSingleOutput("check brew","which", "brew");
        if (response == null || response.isEmpty()) {
            Logger.logSevere("Homebrew not found");
            throw new RuntimeException("Open a terminal and run the following command to install Homebrew: \n\n" +
                    "ruby -e \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)\"");
        }
        Logger.logDebug("Brew found at " + response);

        // Check for ios-deploy installed
        response = ProcessRunner.runProcessForSingleOutput("check ios-deploy","which", "ios-deploy");
        if (response == null || response.isEmpty()) {
            Logger.logSevere("ios-deploy not found. It will be installed now");
            ProcessRunner runner = new ProcessRunner("brew", "install", "ios-deploy");
            if (runner.runProcess("ios-deploy") == 0) {
                Logger.logDebug("ios-deploy installed");
                return getIOSDeployPath();
            } else {
                Logger.logDebug("Error installing ios-deploy");
                return null;
            }
        } else {
            Logger.logDebug("ios-deploy found at " + response);
        }
        return Path.of(response);
    }

    public static String[] connectedDevices() {
        if (bridge == null) {
            bridge = MobileDeviceBridge.instance;
        }

        return bridge.getDeviceIds();
    }

    public static boolean install(String app) throws IOException, InterruptedException {
        Path deploy = getIOSDeployPath();
        if (deploy != null) {
            String[] devices = Deploy.connectedDevices();
            if (devices == null || devices.length == 0) {
                Logger.logSevere("No iOS devices connected to this system. Exit install procedure");
                return false;
            }
            if (devices.length > 1) {
                Logger.logSevere("Multiple iOS devices connected to this system: " + String.join(", ", devices ) + ". We'll use the first one.");
            }
            String deviceId = devices[0];

            ProcessRunner runner = new ProcessRunner(deploy.toString(),
                    "--id", deviceId, "--bundle", app, "--no-wifi", "--debug", "--noninteractive");
            runner.addToEnv("PATH", "/usr/bin/:$PATH");
            runner.setInfo(true);
            boolean result = runner.runTimedProcess("run", 60);
            Logger.logInfo("result = " + result);
            return result;
        }
        return false;
    }
}
