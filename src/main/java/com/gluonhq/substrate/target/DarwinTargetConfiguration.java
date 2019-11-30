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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DarwinTargetConfiguration extends AbstractTargetConfiguration {

    private static final List<String> darwinLibs = Arrays.asList(
            "-llibchelper", "-lpthread",
            "-Wl,-framework,Foundation", "-Wl,-framework,AppKit");

    public DarwinTargetConfiguration(ProcessPaths paths, ProjectConfiguration configuration ) {
        super(paths, configuration);
    }

    @Override
    String getAdditionalSourceFileLocation() {
        return "/native/macosx/";
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        return Arrays.asList("AppDelegate.m", "launcher.c");
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        if (!useJavaFX) {
            return darwinLibs;
        }
        String libPath = "-Wl,-force_load," + projectConfiguration.getJavafxStaticLibsPath() + "/";
        List<String> answer = new ArrayList<>(Arrays.asList(
                libPath + "libprism_es2.a", libPath + "libglass.a",
                libPath + "libjavafx_font.a", libPath + "libjavafx_iio.a"));
        if (usePrismSW) {
            answer.add(libPath + "libprism_sw.a");
        }
        answer.addAll(macoslibs);
        return answer;
    }

    @Override
    List<String> getTargetSpecificLinkLibraries() {
        List<String> defaultLinkFlags = new ArrayList<>(super.getTargetSpecificLinkLibraries());
        defaultLinkFlags.addAll(Arrays.asList("-Wl,-force_load," +
                Path.of(projectConfiguration.getGraalPath(), "lib", "libnet.a").toString(),
                "-lextnet", "-lstdc++"));
        return defaultLinkFlags;
    }

    @Override
    List<String> getTargetSpecificNativeLibsFlags(Path libPath, List<String> libs) {
        return libs.stream()
                .map(s -> "-Wl,-force_load," + libPath.resolve(s))
                .collect(Collectors.toList());
    }

    private static final List<String> macoslibs = Arrays.asList("-lffi",
            "-lpthread", "-lz", "-ldl", "-lstrictmath", "-llibchelper",
            "-ljava", "-lnio", "-lzip", "-ljvm", "-lobjc",
            "-Wl,-framework,Foundation", "-Wl,-framework,AppKit",
            "-Wl,-framework,ApplicationServices", "-Wl,-framework,OpenGL",
            "-Wl,-framework,QuartzCore", "-Wl,-framework,Security");
}
