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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WindowsTargetConfiguration extends AbstractTargetConfiguration {

    private static final List<String> libsForJava = List.of(
            "advapi32", "iphlpapi", "ws2_32", "userenv",
            "java", "jvm", "libchelper", "net", "nio",
            "strictmath", "zip", "prefs", "j2pkcs11", "sunec"
    );

    private static final List<String> libsForJavaFX = List.of(
            "comdlg32", "dwmapi", "gdi32", "imm32",
            "shell32", "uiautomationcore", "urlmon", "winmm"
    );

    private static final List<String> fxLibs = List.of(
            "glass", "javafx_font", "javafx_iio",
            "prism_common", "prism_d3d"
    );

    private static final List<String> fxSwLibs = List.of(
            "prism_sw"
    );

    public WindowsTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration ) {
        super(paths, configuration);
    }

    @Override
    String getAdditionalSourceFileLocation() {
        return "/native/windows/";
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        return Collections.singletonList("launcher.c");
    }

    @Override
    List<String> getTargetSpecificCCompileFlags() {
        return Arrays.asList("/MD", "/D_UNICODE", "/DUNICODE", "/DWIN32", "/D_WINDOWS");
    }

    @Override
    boolean allowHttps() {
        return false;
    }

    /**
     * The arguments to native-image.cmd must be wrapped in double quotes when the
     * argument contains the '=' character.
     */
    @Override
    void postProcessCompilerArguments(List<String> arguments) {
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (argument.contains("=")) {
                arguments.set(i, "\"" + argument + "\"");
            }
        }
    }

    /**
     * On windows, the classpath needs to be wrapped in double quotes.
     */
    @Override
    String processClassPath(String cp) throws IOException {
        return "\"" + super.processClassPath(cp) + "\"";
    }

    @Override
    String getCompiler() {
        return "cl";
    }

    @Override
    String getLinker() {
        return "link";
    }

    @Override
    String getNativeImageCommand() {
        return "native-image.cmd";
    }

    @Override
    String getObjectFileExtension() {
        return "obj";
    }

    @Override
    List<String> getTargetSpecificLinkOutputFlags() {
        return Collections.singletonList("/OUT:" + getAppPath(getLinkOutputName()));
    }

    @Override
    String getLinkOutputName() {
        String appName = projectConfiguration.getAppName();
        return appName + ".exe";
    }

    @Override
    List<String> getTargetSpecificLinkLibraries() {
        return asListOfLibraryLinkFlags(libsForJava);
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        List<String> flags = new ArrayList<>();
        flags.add("/NODEFAULTLIB:libcmt.lib");

        if (useJavaFX) {
            flags.addAll(asListOfLibraryLinkFlags(libsForJavaFX));
            flags.addAll(asListOfLibraryLinkFlags(fxLibs));
            flags.addAll(asListOfWholeArchiveLinkFlags(fxLibs));

            if (usePrismSW) {
                flags.addAll(asListOfLibraryLinkFlags(fxSwLibs));
                flags.addAll(asListOfWholeArchiveLinkFlags(fxSwLibs));
            }
        }

        return flags;
    }

    @Override
    String getLinkLibraryPathOption() {
        return "/LIBPATH:";
    }

    private List<String> asListOfLibraryLinkFlags(List<String> libraries) {
        return libraries.stream()
                .map(library -> library + ".lib")
                .collect(Collectors.toList());
    }

    private List<String> asListOfWholeArchiveLinkFlags(List<String> libraries) {
        return libraries.stream()
                .map(library -> "/WHOLEARCHIVE:" + library + ".lib")
                .collect(Collectors.toList());
    }
}
