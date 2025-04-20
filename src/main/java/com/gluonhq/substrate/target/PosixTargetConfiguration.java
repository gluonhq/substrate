/*
 * Copyright (c) 2019, 2022, 2025, Gluon
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

abstract class PosixTargetConfiguration extends AbstractTargetConfiguration {

    PosixTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration) {
        super(paths, configuration);
    }

    @Override
    protected Path getCLibPath() {
        return super.getCLibPath().resolve("glibc");
    }

    @Override
    void checkPlatformSpecificClibs(Path clibPath) throws IOException {
        Path libjvmPath = clibPath.resolve("libjvm.a");
        if (!Files.exists(libjvmPath)) throw new IOException("Missing library libjvm.a not in linkpath "+clibPath);
    }

    @Override
    public boolean createSharedLib() throws IOException, InterruptedException {
        if (!compile()) {
            Logger.logSevere("Error building a shared image: error compiling the native image");
            return false;
        }
        if (!link()) {
            Logger.logSevere("Error building a shared image: error linking the native image");
            return false;
        }
        return Files.exists(getSharedLibPath());
    }

    abstract Path getSharedLibPath();

}
