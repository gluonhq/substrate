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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public abstract class AbstractTargetConfiguration implements TargetConfiguration {

  //  static String[] C_RESOURCES = { "launcher.c",  "thread.c"};
    ProjectConfiguration projectConfiguration;

    @Override
    public boolean compile(ProcessPaths paths, ProjectConfiguration config, String cp) throws IOException, InterruptedException {
        this.projectConfiguration = config;
        Triplet target =  config.getTargetTriplet();
        String jniPlatform = getJniPlatform(target.getOs());
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
        compileBuilder.command().addAll(getTargetSpecificAOTCompileFlags());
        compileBuilder.command().add("-Dsvm.platform=org.graalvm.nativeimage.Platform$"+jniPlatform);
        compileBuilder.command().add("-cp");
        compileBuilder.command().add(cp);
        compileBuilder.command().add(mainClassName);
        compileBuilder.redirectErrorStream(true);
        Process compileProcess = compileBuilder.start();
        InputStream inputStream = compileProcess.getInputStream();
        asynPrintFromInputStream(inputStream);
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

    private String getJniPlatform( String os ) {
        switch (os) {
            case Constants.OS_LINUX: return "LINUX_AMD64";
            case Constants.OS_IOS:return "DARWIN_AARCH64";
            case Constants.OS_DARWIN: return "DARWIN_AMD64";
            default: throw new IllegalArgumentException("No support yet for " + os);
        }
    }

    @Override
    public boolean link(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {

        if ( !Files.exists(projectConfiguration.getJavaStaticLibsPath())) {
            System.err.println("We can't link because the static Java libraries are missing. " +
                    "The path "+ projectConfiguration.getJavaStaticLibsPath() + " does not exist.");
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
        ProcessBuilder linkBuilder = new ProcessBuilder(getLinker());
        Path appPath = gvmPath.resolve(appName);

        linkBuilder.command().add("-o");
        linkBuilder.command().add(paths.getAppPath().resolve(appName).toString());

        getAdditionalSourceFiles()
              .forEach( r -> linkBuilder.command().add(
                      appPath.resolve(r.replaceAll("\\..*", ".o")).toString()));


        linkBuilder.command().add(objectFile.toString());
        linkBuilder.command().add("-L" + projectConfiguration.getJavaStaticLibsPath());
        linkBuilder.command().add("-L"+ Path.of(projectConfiguration.getGraalPath(), "lib", "svm", "clibraries", target.getOsArch2())); // darwin-amd64");
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
        String cmds = String.join(" ", linkBuilder.command());

        Process compileProcess = linkBuilder.start();
        int result = compileProcess.waitFor();
        if (result != 0 ) {
            System.err.println("Linking failed. Details from linking below:");
            System.err.println("Command was: "+cmds);
            printFromInputStream(compileProcess.getInputStream());
            return false;
        }
        return true;
    }


    private void asynPrintFromInputStream (InputStream inputStream) {
        Thread t = new Thread(() -> {
            try {
                printFromInputStream(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.start();
    }

    private void printFromInputStream(InputStream inputStream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String l = br.readLine();
        while (l != null) {
            System.err.println(l);
            l = br.readLine();
        }
    }

    private static String getNativeImagePath (ProjectConfiguration configuration) {
        String graalPath = configuration.getGraalPath();
        Path path = Path.of(graalPath, "bin", "native-image");
        return path.toString();
    }

    private Process startAppProcess( Path appPath, String appName ) throws IOException {
        ProcessBuilder runBuilder = new ProcessBuilder(appPath.resolve(appName).toString());
        runBuilder.redirectErrorStream(true);
        return runBuilder.start();
    }

    public boolean compileAdditionalSources(ProcessPaths paths, ProjectConfiguration projectConfiguration)
            throws IOException, InterruptedException {

        String appName = projectConfiguration.getAppName();
        Path workDir = paths.getGvmPath().resolve(appName);
        Files.createDirectories(workDir);

        ProcessBuilder processBuilder = new ProcessBuilder(getCompiler());
        processBuilder.command().add("-c");
        if (projectConfiguration.isVerbose()) {
            processBuilder.command().add("-DGVM_VERBOSE");
        }
        processBuilder.command().addAll(getTargetSpecificCCompileFlags());
        for( String fileName: getAdditionalSourceFiles() ) {
            FileOps.copyResource(getAdditionalSourceFileLocation()  + fileName, workDir.resolve(fileName));
            processBuilder.command().add(fileName);
        }
        processBuilder.command().addAll(getTargetSpecificCCompileFlags());
        processBuilder.directory(workDir.toFile());
        String cmds = String.join(" ", processBuilder.command());
        processBuilder.redirectErrorStream(true);
        Process p = processBuilder.start();
        int result = p.waitFor();
        if (result != 0) {
            System.err.println("Compilation of additional sources failed with result = " + result);
            System.err.println("Original command was "+cmds);
            printFromInputStream(p.getInputStream());
            return false;
        } // we need more checks (e.g. do launcher.o and thread.o exist?)
        return true;
    }

    @Override
    public InputStream run(Path appPath, String appName) throws IOException {
        Process runProcess = startAppProcess(appPath,appName);
        return runProcess.getInputStream();
    }


    @Override
    public boolean runUntilEnd(Path appPath, String appName) throws IOException, InterruptedException {
        Process runProcess = startAppProcess(appPath,appName);
        InputStream is = runProcess.getInputStream();
        asynPrintFromInputStream(is);
        int result = runProcess.waitFor();
        if (result != 0 ) {
            printFromInputStream(is);
            return false;
        }
        return true;
    }

    // Default settings below, can be overridden by subclasses

    String getAdditionalSourceFileLocation() {
        return "/native/linux/";
    }

    List<String> getAdditionalSourceFiles() {
        return Arrays.asList("launcher.c","thread.c");
    }

    String getCompiler() {
        return "gcc";
    }

    String getLinker() {
        return "gcc";
    }

    List<String> getTargetSpecificLinkFlags() {
        return new LinkedList<>();
    }

    List<String> getTargetSpecificCCompileFlags() {
        return new LinkedList<>();
    }

    List<String> getTargetSpecificAOTCompileFlags() {
        return new LinkedList<>();
    }

}
