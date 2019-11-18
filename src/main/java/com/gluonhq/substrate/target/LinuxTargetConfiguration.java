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
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.Version;
import com.gluonhq.substrate.util.VersionParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class LinuxTargetConfiguration extends AbstractTargetConfiguration {

    private static final Version COMPILER_MINIMAL_VERSION = new Version(6);
    private static final Version LINKER_MINIMAL_VERSION = new Version(2, 26);

    @Override
    public boolean link(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {
        checkCompiler();
        checkLinker();
        return super.link(paths, projectConfiguration);
    }

    private static final List<String> linuxLibs = Arrays.asList("-llibchelper", "-lpthread");

    private static final List<String> linuxfxlibs = Arrays.asList( "-Wl,--whole-archive",
            "-lprism_es2", "-lglass", "-lglassgtk3", "-ljavafx_font",
            "-ljavafx_font_freetype", "-ljavafx_font_pango", "-ljavafx_iio",
            "-Wl,--no-whole-archive", "-lGL", "-lX11","-lgtk-3", "-lgdk-3",
            "-lpangocairo-1.0", "-lpango-1.0", "-latk-1.0",
            "-lcairo-gobject", "-lcairo", "-lgdk_pixbuf-2.0",
            "-lgio-2.0", "-lgobject-2.0", "-lglib-2.0", "-lfreetype", "-lpangoft2-1.0",
            "-lgthread-2.0", "-lstdc++", "-lz", "-lXtst"
            );

    private static final List<String> linuxfxSWlibs = Arrays.asList(
            "-Wl,--whole-archive", "-lprism_sw", "-Wl,--no-whole-archive", "-lm");

    @Override
    List<String> getCommonLinkLibraries() {
        List<String> defaultLinkFlags = new ArrayList<>(super.getCommonLinkLibraries());
        defaultLinkFlags.add("-lextnet");
        return defaultLinkFlags;
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        List<String> answer = new LinkedList<>();
        answer.add("-rdynamic");
        answer.addAll(linuxLibs);
        if (!useJavaFX) return answer;

        ProcessBuilder process = new ProcessBuilder("pkg-config", "--libs", "gtk+-3.0", "gthread-2.0", "xtst");
        process.redirectErrorStream(true);
        try {
            Process start = process.start();
            InputStream is = start.getInputStream();
            start.waitFor();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) {
                Logger.logInfo("[SUB] " + line);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        answer.addAll(linuxfxlibs);
        if (usePrismSW) {
            answer.addAll(linuxfxSWlibs);
        }
        return answer;
    }

    private void checkCompiler() throws IOException, InterruptedException {
        validateVersion(new String[] { "gcc", "--version" }, "compiler", COMPILER_MINIMAL_VERSION);
    }

    private void checkLinker() throws InterruptedException, IOException {
        validateVersion(new String[] { "ld", "--version" }, "linker", LINKER_MINIMAL_VERSION);
    }

    private void validateVersion(String[] processCommand, String processName, Version minimalVersion) throws InterruptedException, IOException {
        String versionLine = getFirstLineFromProcess(processCommand);
        if (versionLine == null) {
            System.err.println("WARNING: we were unable to parse the version of your " + processName + ".\n" +
                    "         The build will continue, but please bare in mind that the minimal required version for " + processCommand[0] + " is " + minimalVersion + ".");
        } else {
            VersionParser versionParser = new VersionParser();
            Version version = versionParser.parseVersion(versionLine);
            if (version == null) {
                System.err.println("WARNING: we were unable to parse the version of your " + processName + ": \"" + versionLine + "\".\n" +
                        "         The build will continue, but please bare in mind that the minimal required version for " + processCommand[0] + " is \"" + minimalVersion + "\".");
            } else if (version.compareTo(minimalVersion) < 0) {
                System.err.println("ERROR: The version of your " + processName + ": \"" + version + "\", does not match the minimal required version: \"" + minimalVersion + "\".");
                throw new IllegalArgumentException(processCommand[0] + " version too old");
            }
        }
    }

    private String getFirstLineFromProcess(String...command) throws InterruptedException, IOException {
        ProcessBuilder compiler = new ProcessBuilder(command);
        compiler.redirectErrorStream(true);

        Process compilerProcess = compiler.start();
        InputStream processInputStream = compilerProcess.getInputStream();
        compilerProcess.waitFor();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(processInputStream))) {
            return reader.readLine();
        }
    }
}
