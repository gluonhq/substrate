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

import java.util.Arrays;
import java.util.List;

public class WindowsTargetConfiguration extends AbstractTargetConfiguration {

    @Override
    String getAdditionalSourceFileLocation() {
        return "/native/windows/";
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        return Arrays.asList("launcher.c");
    }

    @Override
    List<String> getTargetSpecificCCompileFlags() {
        return Arrays.asList("/MT");
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
    void specifyLinkProcessOutput(ProcessBuilder linkBuilder, String appName) {
        linkBuilder.command().add("/OUT:" + getAppPath(appName + ".exe"));
    }

    @Override
    void specifyLinkProcessLibraries(ProcessBuilder linkBuilder) {
        linkBuilder.command().add("advapi32.lib");
        linkBuilder.command().add("iphlpapi.lib");
        linkBuilder.command().add("ws2_32.lib");

        linkBuilder.command().add("java.lib");
        linkBuilder.command().add("jvm.lib");
        linkBuilder.command().add("libchelper.lib");
        linkBuilder.command().add("net.lib");
        linkBuilder.command().add("nio.lib");
        linkBuilder.command().add("strictmath.lib");
        linkBuilder.command().add("zip.lib");
    }

    @Override
    String getLinkLibraryPathOption() {
        return "/LIBPATH:";
    }
}
