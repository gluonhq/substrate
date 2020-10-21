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
package com.gluonhq.substrate.model;

import com.gluonhq.substrate.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ProcessPaths {

    private final Path clientPath;
    private final Path appPath;
    private final Path gvmPath;
    private final Path genPath;
    private final Path tmpPath;
    private final Path logPath;
    private final Path sourcePath;
    private final Path nativeCodePath;

    /**
     * |-- target/build
     *     |-- client                   <!-- buildRoot  -->
     *         |-- log
     *         |-- $arch-$os            <!-- $ARCH-$OS  -->
     *             |-- gvm
     *                 |-- $appName
     *                 |-- lib
     *                 |-- log
     *                 |-- tmp
     *                 |-- android_project
     *                     |-- app
     *                 |-- $appName.apk
     *             |-- $appName (.exe,.app)
     *             |-- $appName.app.dSYM
     *             |-- $appName.ipa
     *             |-- gensrc
     *                 |-- android
     *                 |-- ios
     * |-- src
     *     |-- android                  <!-- optional  -->
     *     |-- ios                      <!-- optional  -->
     *     |-- main
     *         |-- java
     *         |-- resources
     */

    public ProcessPaths(Path buildRoot, String app) throws IOException {
        clientPath = buildRoot != null ? buildRoot : Paths.get(System.getProperty("user.dir"));
        appPath = Files.createDirectories(clientPath.resolve(app));
        gvmPath = Files.createDirectories(appPath.resolve(Constants.GVM_PATH));
        genPath = Files.createDirectories(appPath.resolve(Constants.GEN_PATH));
        tmpPath = Files.createDirectories(gvmPath.resolve(Constants.TMP_PATH));
        logPath = Files.createDirectories(gvmPath.resolve(Constants.LOG_PATH));
        sourcePath = clientPath.getParent().getParent().resolve(Constants.SOURCE_PATH);
        nativeCodePath = sourcePath.getParent().resolve(Constants.NATIVE_CODE_PATH);
    }

    public Path getClientPath() {
        return clientPath;
    }

    public Path getAppPath() {
        return appPath;
    }

    public Path getGvmPath() {
        return gvmPath;
    }

    public Path getGenPath() {
        return genPath;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public Path getTmpPath() {
        return tmpPath;
    }

    public Path getLogPath() {
        return logPath;
    }

    public Path getNativeCodePath() {
        return nativeCodePath;
    }
}

