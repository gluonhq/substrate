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

import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.ProjectConfiguration;
import com.gluonhq.substrate.util.FileOps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

public class LinuxTargetConfiguration extends AbstractTargetConfiguration {

    @Override
    public boolean link(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {
        checkLinker();
        return super.link(paths, projectConfiguration);
    }

    @Override
    List<String> getTargetSpecificLinkFlags() {
        return List.of();
    }



    private boolean checkLinker() throws IOException, InterruptedException {
        ProcessBuilder linker = new ProcessBuilder("gcc");
        linker.command().add("--version");
        linker.redirectErrorStream(true);
        Process start = linker.start();
        InputStream is = start.getInputStream();
        start.waitFor();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String l = br.readLine();
        int ar = l.lastIndexOf(")");
        if ((ar < 0) || (ar > l.length() - 2)) {
            // can't parse... let's try but warn
            System.err.println("WARNING: your gcc compiler has version " + l + " which might not work");
            return true;
        }
        String versionString = l.substring(ar + 1).trim();
        String v = versionString.substring(0, 1);
        try {
            int version = Integer.parseInt(v);
            if (version < 6) {
                System.err.println("Wrong GCC version: " + l + ". We need at least gcc 6. // todo where to get it?");
                throw new IllegalArgumentException("gcc version outdated");
            } else {
                return true;
            }
        } catch (NumberFormatException e ) {
            System.err.println("WARNING: your gcc compiler has version " + l + " which we could not parse so it might not work");
        }
        return true;

    }

}
