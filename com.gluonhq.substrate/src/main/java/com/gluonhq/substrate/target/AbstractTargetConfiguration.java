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

import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.ProjectConfiguration;
import com.gluonhq.substrate.model.Triplet;
import com.gluonhq.substrate.util.FileOps;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

public abstract class AbstractTargetConfiguration implements TargetConfiguration {


    @Override
    public boolean compile(ProcessPaths paths, ProjectConfiguration config, String cp) throws IOException, InterruptedException {
        Triplet target =  config.getTargetTriplet();
        String jniPlatform = null;
        if (target.getOs().equals(Constants.OS_LINUX)) {
            jniPlatform="LINUX_AMD64";
        } else if (target.getOs().equals(Constants.OS_DARWIN)) {
            jniPlatform="DARWIN_AMD64";
        } else {
            throw new IllegalArgumentException("No support yet for "+target.getOs());
        }
        if (!compileAdditionalSources(paths, config) ) {
            return false;
        }
        Path gvmPath = paths.getGvmPath();
        FileOps.rmdir(paths.getTmpPath());
        String tmpDir = paths.getTmpPath().toFile().getAbsolutePath();
        String mainClassName = config.getMainClassName();
        if (mainClassName == null || mainClassName.isEmpty()) {
            throw new IllegalArgumentException("No main class is supplied. Cannot compile.");
        }
        if (cp == null || cp.isEmpty()) {
            throw new IllegalArgumentException("No classpath specified. Cannot compile");
        }
        String nativeImage = getNativeImagePath(config);
        ProcessBuilder compileBuilder = new ProcessBuilder(nativeImage);
        compileBuilder.command().add("-H:+ExitAfterRelocatableImageWrite");
        compileBuilder.command().add("-H:TempDirectory="+tmpDir);
        compileBuilder.command().add("-H:+SharedLibrary");
        compileBuilder.command().add("-Dsvm.platform=org.graalvm.nativeimage.Platform$"+jniPlatform);
        compileBuilder.command().add("-cp");
        compileBuilder.command().add(cp);
        compileBuilder.command().add(mainClassName);
        compileBuilder.redirectErrorStream(true);
        Process compileProcess = compileBuilder.start();
        InputStream inputStream = compileProcess.getInputStream();
        int result = compileProcess.waitFor();
        // we will print the output of the process only if we don't have the resulting objectfile

        boolean failure = result != 0;
        String extraMessage = null;
        if (!failure) {
            String nameSearch = mainClassName.toLowerCase()+".o";
            Path p = FileOps.findFile(gvmPath, nameSearch);
            if (p == null) {
                failure = true;
                extraMessage = "Objectfile should be called "+nameSearch+" but we didn't find that under "+gvmPath.toString();
            }
        }
        if (failure) {
            System.err.println("Compilation failed with result = " + result);
            printFromInputStream(inputStream);

            if (extraMessage!= null) {
                System.err.println("Additional information: "+extraMessage);
            }
        }
        return !failure;
    }


    public abstract boolean compileAdditionalSources(ProcessPaths paths, ProjectConfiguration projectConfiguration)
            throws IOException, InterruptedException;


    @Override
    public boolean link(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {
        File javaStaticLibsDir = projectConfiguration.getJavaStaticLibsPath().toFile();
        if (!javaStaticLibsDir.exists()) {
            System.err.println("We can't link because the static Java libraries are missing. " +
                    "The path "+javaStaticLibsDir+" does not exist.");
            return false;
        }
        String appName = projectConfiguration.getAppName();
        String objectFilename = projectConfiguration.getMainClassName().toLowerCase()+".o";
        Triplet target = projectConfiguration.getTargetTriplet();
        Path gvmPath = paths.getGvmPath();
        Path objectFile = FileOps.findFile(gvmPath, objectFilename);
        if (objectFile == null) {
            throw new IllegalArgumentException("Linking failed, since there is no objectfile named "+objectFilename+" under "
                    +gvmPath.toString());
        }
        ProcessBuilder linkBuilder = new ProcessBuilder("gcc");
        Path linux = gvmPath.resolve(appName);

        linkBuilder.command().add("-o");
        linkBuilder.command().add(paths.getAppPath().toString() + "/" + appName);
        linkBuilder.command().add(linux.toString() + "/launcher.o");
        linkBuilder.command().add(linux.toString() + "/thread.o");
        linkBuilder.command().add(objectFile.toString());
        linkBuilder.command().add("-L" + projectConfiguration.getJavaStaticLibsPath());
        linkBuilder.command().add("-L"+projectConfiguration.getGraalPath()+"/lib/svm/clibraries/"+target.getOsArch2());// darwin-amd64");
        linkBuilder.command().add("-ljava");
        linkBuilder.command().add("-ljvm");
        linkBuilder.command().add("-llibchelper");
        linkBuilder.command().add("-lnio");
        linkBuilder.command().add("-lzip");
        linkBuilder.command().add("-lnet");
        linkBuilder.command().add("-lpthread");
        linkBuilder.command().add("-lz");
        linkBuilder.command().add("-ldl");
        linkBuilder.command().addAll(getTargetSpecificLinkFlags());
        linkBuilder.redirectErrorStream(true);
        Process compileProcess = linkBuilder.start();
        InputStream inputStream = compileProcess.getInputStream();
        int result = compileProcess.waitFor();
        if (result != 0 ) {
            System.err.println("Linking failed. Details from linking below:");
            printFromInputStream(inputStream);
            return false;
        }
        return true;
    }

    abstract List<String> getTargetSpecificLinkFlags();

    void asynPrintFromInputStream (InputStream inputStream) throws IOException {
        Thread t = new Thread() {
            @Override public void run() {
                try {
                    printFromInputStream(inputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
    }

    void printFromInputStream(InputStream inputStream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String l = br.readLine();
        while (l != null) {
            System.err.println(l);
            l = br.readLine();
        }
    }

    static String getNativeImagePath (ProjectConfiguration configuration) {
        String graalPath = configuration.getGraalPath();
        Path path = Path.of(graalPath, "bin", "native-image");
        return path.toString();
    }
}
