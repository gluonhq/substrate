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
import com.gluonhq.substrate.model.Triplet;
import com.gluonhq.substrate.util.FileDeps;
import com.gluonhq.substrate.util.FileOps;

import java.io.IOException;
import java.nio.file.Path;
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
    public boolean link(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {
        this.paths = paths;
        this.projectConfiguration = projectConfiguration;

        Path javaSDKPath = FileDeps.getJavaSDKPath(projectConfiguration);
        String appName = projectConfiguration.getAppName() + ".exe";
        String objectFilename = projectConfiguration.getMainClassName().toLowerCase() + "." + getObjectFileExtension();
        Triplet target = projectConfiguration.getTargetTriplet();
        Path gvmPath = paths.getGvmPath();
        Path objectFile = FileOps.findFile(gvmPath, objectFilename);
        if (objectFile == null) {
            throw new IllegalArgumentException("Linking failed, since there is no objectfile named "+objectFilename+" under "
                    +gvmPath.toString());
        }
        ProcessBuilder linkBuilder = new ProcessBuilder(getLinker());

        linkBuilder.command().add("/OUT:" + getAppPath(appName));

        Path gvmAppPath = gvmPath.resolve(appName);
        getAdditionalSourceFiles()
                .forEach( r -> linkBuilder.command().add(
                        gvmAppPath.resolve(r.replaceAll("\\..*", "." + getObjectFileExtension())).toString()));

        linkBuilder.command().add(objectFile.toString());
        linkBuilder.command().addAll(getTargetSpecificObjectFiles());

        linkBuilder.command().add("/LIBPATH:" + javaSDKPath);
        if (projectConfiguration.isUseJavaFX()) {
            Path javafxSDKPath = FileDeps.getJavaFXSDKLibsPath(projectConfiguration);
            linkBuilder.command().add("/LIBPATH:" + javafxSDKPath);
        }

        linkBuilder.command().add("/LIBPATH:"+ Path.of(projectConfiguration.getGraalPath(), "lib", "svm", "clibraries", target.getOsArch2()));
//        linkBuilder.command().add("-ljava");
//        linkBuilder.command().add("-ljvm");
//        linkBuilder.command().add("-llibchelper");
//        linkBuilder.command().add("-lnio");
//        linkBuilder.command().add("-lzip");
//        linkBuilder.command().add("-lnet");
//        linkBuilder.command().add("-lstrictmath");
//        linkBuilder.command().add("-lpthread");
//        linkBuilder.command().add("-lz");
//        linkBuilder.command().add("-ldl");
//        linkBuilder.command().addAll(getTargetSpecificLinkFlags(projectConfiguration.isUseJavaFX(), projectConfiguration.isUsePrismSW()));
        linkBuilder.redirectErrorStream(true);
        String cmds = String.join(" ", linkBuilder.command());
        System.err.println("cmd = "+cmds);
        Process compileProcess = linkBuilder.start();
        System.err.println("started linking");
        int result = compileProcess.waitFor();
        System.err.println("done linking");
        if (result != 0) {
            System.err.println("Linking failed. Details from linking below:");
            System.err.println("Command was: "+cmds);
            printFromInputStream(compileProcess.getInputStream());
            return false;
        }
        return true;
    }
}
