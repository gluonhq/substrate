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
package com.gluonhq.substrate.target;

import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

abstract class PosixTargetConfiguration extends AbstractTargetConfiguration {

    PosixTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration) {
        super(paths, configuration);

        if (this instanceof DarwinTargetConfiguration || this instanceof IosTargetConfiguration) {
            checkGraalVMPermissions(configuration.getGraalPath().toString());
        }
    }

    @Override
    void checkPlatformSpecificClibs(Path clibPath) throws IOException {
        Path libjvmPath = clibPath.resolve("libjvm.a");
        if (!Files.exists(libjvmPath)) throw new IOException("Missing library libjvm.a not in linkpath "+clibPath);
    }

    /**
     * Prevent undesired dialogs and build failures when running on MacOs 1.15.0+.
     *
     * By default, the OS prevents the access to any non-notarized executable if it
     * has been downloaded.
     *
     * This method will check if the GraalVM folder is under quarantine, and if that
     * is the case, it will remove it recursively from all the files, without the
     * need of deactivating GateKeeper.
     *
     * See https://github.com/oracle/graal/issues/1724
     *
     * @param graalvmHome the path to GraalVM
     */
    private void checkGraalVMPermissions(String graalvmHome) {
        if (graalvmHome == null) {
            return;
        }
        Logger.logDebug("Checking execution permissions for " + graalvmHome);
        try {
            String response = ProcessRunner.runProcessForSingleOutput("check attr", "xattr", "-p", "com.apple.quarantine", graalvmHome);
            if (response != null && !response.contains("No such xattr")) {
                ProcessRunner runner = new ProcessRunner("xattr", "-r", "-d", "com.apple.quarantine", graalvmHome);
                if (runner.runProcess("remove quarantine") != 0) {
                    throw new IOException("Error removing quarantine from " + graalvmHome);
                }
                Logger.logDebug("Quarantine attributes removed successfully");
            }
        } catch (IOException | InterruptedException e) {
            Logger.logFatal(e,"Error checking execution permissions for " + graalvmHome);
        }
    }

}
