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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

public class LinuxTargetConfiguration extends AbstractTargetConfiguration {

    public boolean compileAdditionalSources(ProcessPaths paths, ProjectConfiguration projectConfiguration)
            throws IOException, InterruptedException {
        String appName = projectConfiguration.getAppName();
        Path workDir = paths.getGvmPath().resolve(appName);
        Files.createDirectories(workDir);
        FileOps.copyResource("/native/linux/launcher.c", workDir.resolve("launcher.c"));
        FileOps.copyResource("/native/linux/thread.c", workDir.resolve("thread.c"));
        ProcessBuilder processBuilder = new ProcessBuilder("gcc");
        processBuilder.command().add("-c");
        if (projectConfiguration.isVerbose()) {
            processBuilder.command().add("-DGVM_VERBOSE");
        }
        processBuilder.command().add("launcher.c");
        processBuilder.command().add("thread.c");
        processBuilder.directory(workDir.toFile());
        String cmds = String.join(" ", processBuilder.command());
        processBuilder.redirectErrorStream(true);
        Process p = processBuilder.start();
        InputStream inputStream = p.getInputStream();
        int result = p.waitFor();
        if (result != 0) {
            System.err.println("Compilation of additional sources failed with result = " + result);
            printFromInputStream(inputStream);
            return false;
        } // we need more checks (e.g. do launcher.o and thread.o exist?)
        return true;
    }

    @Override
    public boolean link(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {
        checkLinker();
        return super.link(paths, projectConfiguration);
    }
//        File javaStaticLibsDir = projectConfiguration.getJavaStaticLibsPath().toFile();
//        if (!javaStaticLibsDir.exists()) {
//            System.err.println("We can't link because the static Java libraries are missing. " +
//                    "The path "+javaStaticLibsDir+" does not exist.");
//            return false;
//        }
//        String appName = projectConfiguration.getAppName();
//        String objectFilename = projectConfiguration.getMainClassName().toLowerCase()+".o";
//        Path gvmPath = paths.getGvmPath();
//        Path objectFile = FileOps.findFile(gvmPath, objectFilename);
//        if (objectFile == null) {
//            throw new IllegalArgumentException("Linking failed, since there is no objectfile named "+objectFilename+" under "
//                    +gvmPath.toString());
//        }
//        ProcessBuilder linkBuilder = new ProcessBuilder("gcc");
//        Path linux = gvmPath.resolve(appName);
//
//        linkBuilder.command().add("-o");
//        linkBuilder.command().add(paths.getAppPath().toString() + "/" + appName);
//        linkBuilder.command().add(linux.toString() + "/launcher.o");
//        linkBuilder.command().add(linux.toString() + "/thread.o");
//        linkBuilder.command().add(objectFile.toString());
//        linkBuilder.command().add("-L" + projectConfiguration.getJavaStaticLibsPath());
//        linkBuilder.command().add("-L"+projectConfiguration.getGraalPath()+"/lib/svm/clibraries/linux-amd64");
//        linkBuilder.command().add("-ljava");
//        linkBuilder.command().add("-ljvm");
//        linkBuilder.command().add("-llibchelper");
//        linkBuilder.command().add("-lnio");
//        linkBuilder.command().add("-lzip");
//        linkBuilder.command().add("-lnet");
//        linkBuilder.command().add("-lpthread");
//        linkBuilder.command().add("-lz");
//        linkBuilder.command().add("-ldl");
//        linkBuilder.redirectErrorStream(true);
//        Process compileProcess = linkBuilder.start();
//        InputStream inputStream = compileProcess.getInputStream();
//        int result = compileProcess.waitFor();
//        if (result != 0 ) {
//            System.err.println("Linking failed. Details from linking below:");
//            printFromInputStream(inputStream);
//            return false;
//        }
//        return true;
//    }

    @Override
    List<String> getTargetSpecificLinkFlags() {
        LinkedList<String> answer = new LinkedList<>();
        return answer;
    }
    @Override
    public InputStream run(Path appPath, String appName) throws IOException, InterruptedException {
        ProcessBuilder runBuilder = new ProcessBuilder(appPath.toString() + "/" + appName);
        runBuilder.redirectErrorStream(true);
        Process runProcess = runBuilder.start();
        InputStream is = runProcess.getInputStream();
        return is;
    }


    @Override
    public boolean runUntilEnd(Path appPath, String appName) throws IOException, InterruptedException {
        ProcessBuilder runBuilder = new ProcessBuilder(appPath.toString() + "/" + appName);
        runBuilder.redirectErrorStream(true);
        Process runProcess = runBuilder.start();
        InputStream is = runProcess.getInputStream();
        asynPrintFromInputStream(is);
        int result = runProcess.waitFor();
        if (result != 0 ) {
            printFromInputStream(is);
            return false;
        }
        return true;
    }

    boolean checkLinker() throws IOException, InterruptedException {
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
