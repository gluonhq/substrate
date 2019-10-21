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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractTargetConfiguration implements TargetConfiguration {

  //  static String[] C_RESOURCES = { "launcher.c",  "thread.c"};
    ProjectConfiguration projectConfiguration;
    ProcessPaths paths;

    private List<String> defaultAdditionalSourceFiles = Arrays.asList("launcher.c", "thread.c");

    @Override
    public boolean compile(ProcessPaths paths, ProjectConfiguration config, String cp) throws IOException, InterruptedException {
        this.projectConfiguration = config;
        this.paths = paths;
        Triplet target =  config.getTargetTriplet();
        String suffix = target.getArchOs();
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
        compileBuilder.command().add("--report-unsupported-elements-at-runtime");
        compileBuilder.command().add("-Djdk.internal.lambda.eagerlyInitialize=false");
        compileBuilder.command().add("-H:+ExitAfterRelocatableImageWrite");
        compileBuilder.command().add("-H:TempDirectory="+tmpDir);
        compileBuilder.command().add("-H:+SharedLibrary");
        compileBuilder.command().add("-H:ReflectionConfigurationFiles=" + createReflectionConfig(suffix));
        compileBuilder.command().add("-H:JNIConfigurationFiles=" + createJNIConfig(suffix));
        compileBuilder.command().addAll(getResources());
        compileBuilder.command().addAll(getTargetSpecificAOTCompileFlags());
        if (!getBundlesList().isEmpty()) {
            compileBuilder.command().add("-H:IncludeResourceBundles=" + String.join(",", getBundlesList()));
        }
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

        this.paths = paths;
        this.projectConfiguration = projectConfiguration;
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
        linkBuilder.command().addAll(getTargetSpecificObjectFiles());
        linkBuilder.command().add("-L" + projectConfiguration.getJavaStaticLibsPath());
        if (projectConfiguration.isUseJavaFX()) {
            linkBuilder.command().add("-L" + projectConfiguration.getJavafxStaticLibsPath());
        }
        linkBuilder.command().add("-L"+ Path.of(projectConfiguration.getGraalPath(), "lib", "svm", "clibraries", target.getOsArch2())); // darwin-amd64");
        linkBuilder.command().add("-ljava");
        linkBuilder.command().add("-ljvm");
        linkBuilder.command().add("-llibchelper");
        linkBuilder.command().add("-lnio");
        linkBuilder.command().add("-lzip");
        linkBuilder.command().add("-lnet");
        linkBuilder.command().add("-lstrictmath");
        linkBuilder.command().add("-lpthread");
        linkBuilder.command().add("-lz");
        linkBuilder.command().add("-ldl");
        linkBuilder.command().addAll(getTargetSpecificLinkFlags(projectConfiguration.isUseJavaFX(), projectConfiguration.isUsePrismSW()));
        linkBuilder.redirectErrorStream(true);
        String cmds = String.join(" ", linkBuilder.command());
        System.err.println("cmd = "+cmds);
        Process compileProcess = linkBuilder.start();
        System.err.println("started linking");
        int result = compileProcess.waitFor();
        System.err.println("done linking");
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

    List<String> getJavaFXReflectionClassList() {
        return javafxReflectionClassList;
    }

    List<String> getJavaFXSWReflectionClassList() {
        return javafxSWReflectionClassList;
    }

    List<String> getJNIClassList(boolean useJavaFX, boolean usePrismSW) {
        if (!useJavaFX) return Collections.emptyList();
        List<String> answer = new LinkedList<>();
        answer.addAll(javaJNIClassList);
        answer.addAll(javafxJNIClassList);
        if (usePrismSW) {
            answer.addAll(javafxSWJNIClassList);
        }
        return answer;
    }


    private static final List<String> resourcesList = Arrays.asList(
            "frag", "fxml", "css", "gls", "ttf",
            "png", "jpg", "jpeg", "gif", "bmp",
            "license", "json");

    private  List<String> getResources() {
        List<String> resources = new ArrayList<>(resourcesList);
        resources.addAll(projectConfiguration.getResourcesList());

        List<String> list = resources.stream()
                .map(s -> "-H:IncludeResources=.*/.*" + s + "$")
                .collect(Collectors.toList());
        list.addAll(resources.stream()
                .map(s -> "-H:IncludeResources=.*" + s + "$")
                .collect(Collectors.toList()));
        return list;
    }

    private static final List<String> bundlesList = new ArrayList<>(Arrays.asList(
            "com/sun/javafx/scene/control/skin/resources/controls",
            "com.sun.javafx.tk.quantum.QuantumMessagesBundle"
    ));

    private List<String> getBundlesList() {
        if (projectConfiguration.isUseJavaFX()) {
            return bundlesList;
        }
        return Collections.emptyList();
    }

    private Path createReflectionConfig(String suffix) throws IOException {
        Path gvmPath = paths.getGvmPath();
        Path reflectionPath = gvmPath.resolve("reflectionconfig-" + suffix + ".json");
        File f = reflectionPath.toFile();
        if (f.exists()) {
            f.delete();
        }
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)))) {
            bw.write("[\n");
            writeSingleEntry(bw, projectConfiguration.getMainClassName(), false);
            if (projectConfiguration.isUseJavaFX()) {
                for (String javafxClass : getJavaFXReflectionClassList()) {
                    writeEntry(bw, javafxClass);
                }
                if (projectConfiguration.isUsePrismSW()) {
                    for (String javafxClass : getJavaFXSWReflectionClassList()) {
                        writeEntry(bw, javafxClass);
                    }
                }
            }
            bw.write("]");
        }
        return reflectionPath;
    }

    private Path createJNIConfig(String suffix) throws IOException {
        Path gvmPath = paths.getGvmPath();
        Path jniPath = gvmPath.resolve("jniconfig-" + suffix + ".json");
        File f = jniPath.toFile();
        if (f.exists()) {
            f.delete();
        }
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)))) {
            bw.write("[\n");
            bw.write("  {\n    \"name\" : \"" + projectConfiguration.getMainClassName() + "\"\n  }\n");
            for (String javaClass : getJNIClassList(projectConfiguration.isUseJavaFX(), projectConfiguration.isUsePrismSW())) {
                // TODO: create list of exclusions
                writeEntry(bw, javaClass,
                        "mac".equals(suffix) && javaClass.equals("java.lang.Thread"));
            }
            bw.write("]");
        }
        return jniPath;
    }



    private static void writeEntry(BufferedWriter bw, String javaClass) throws IOException {
        writeEntry(bw, javaClass, false);
    }

    private static void writeEntry(BufferedWriter bw, String javaClass, boolean exclude) throws IOException {
        bw.write(",\n");
        writeSingleEntry(bw, javaClass, exclude);
    }

    private static void writeSingleEntry (BufferedWriter bw, String javaClass, boolean exclude) throws IOException {
        bw.write("  {\n");
        bw.write("    \"name\" : \"" + javaClass + "\"");
        if (! exclude) {
            bw.write(",\n");
            bw.write("    \"allDeclaredConstructors\" : true,\n");
            bw.write("    \"allPublicConstructors\" : true,\n");
            bw.write("    \"allDeclaredFields\" : true,\n");
            bw.write("    \"allPublicFields\" : true,\n");
            bw.write("    \"allDeclaredMethods\" : true,\n");
            bw.write("    \"allPublicMethods\" : true\n");
        } else {
            bw.write("\n");
        }
        bw.write("  }\n");
    }

    private static final List<String> javafxReflectionClassList = new ArrayList<>(Arrays.asList(
            "java.lang.Runnable",
            "java.net.InetAddress",
            "java.nio.ByteBuffer",
            "java.nio.ByteOrder",
            "javafx.geometry.Pos",
            "javafx.geometry.HPos",
            "javafx.geometry.Insets",
            "javafx.geometry.VPos",
            "javafx.scene.control.Control",
            "javafx.scene.layout.AnchorPane",
            "javafx.scene.layout.BorderPane",
            "javafx.scene.layout.ColumnConstraints",
            "javafx.scene.layout.FlowPane",
            "javafx.scene.layout.GridPane",
            "javafx.scene.layout.HBox",
            "javafx.scene.layout.Pane",
            "javafx.scene.layout.Priority",
            "javafx.scene.layout.Region",
            "javafx.scene.layout.RowConstraints",
            "javafx.scene.layout.StackPane",
            "javafx.scene.layout.TilePane",
            "javafx.scene.layout.VBox",
            "javafx.scene.Camera",
            "javafx.scene.Group",
            "javafx.scene.Node",
            "javafx.scene.Parent",
            "javafx.scene.Scene",
            "javafx.scene.ParallelCamera",
            "javafx.scene.text.Font",
            "javafx.scene.text.Text",
            "javafx.scene.text.TextFlow",
            "javafx.stage.PopupWindow",
            "javafx.stage.Stage",
            "javafx.stage.Window",
            "javafx.scene.effect.Effect",
            "javafx.scene.image.Image",
            "javafx.scene.image.ImageView",
            "javafx.scene.input.TouchPoint",
            "javafx.scene.paint.Color",
            "javafx.scene.paint.Paint",
            "javafx.scene.shape.Arc",
            "javafx.scene.shape.ArcTo",
            "javafx.scene.shape.Circle",
            "javafx.scene.shape.ClosePath",
            "javafx.scene.shape.CubicCurve",
            "javafx.scene.shape.CubicCurveTo",
            "javafx.scene.shape.HLineTo",
            "javafx.scene.shape.Line",
            "javafx.scene.shape.LineTo",
            "javafx.scene.shape.MoveTo",
            "javafx.scene.shape.Path",
            "javafx.scene.shape.PathElement",
            "javafx.scene.shape.Polygon",
            "javafx.scene.shape.Rectangle",
            "javafx.scene.shape.QuadCurve",
            "javafx.scene.shape.QuadCurveTo",
            "javafx.scene.shape.Shape",
            "javafx.scene.shape.StrokeType",
            "javafx.scene.shape.SVGPath",
            "javafx.scene.shape.VLineTo",
            "javafx.scene.transform.Transform",
            "javafx.animation.KeyFrame",
            "javafx.animation.KeyValue",
            "com.sun.javafx.reflect.Trampoline",
            "com.sun.javafx.scene.control.skin.Utils",
            "com.sun.javafx.tk.quantum.QuantumToolkit",
            "com.sun.prism.shader.AlphaOne_Color_Loader",
            "com.sun.prism.shader.AlphaOne_ImagePattern_Loader",
            "com.sun.prism.shader.AlphaOne_LinearGradient_Loader",
            "com.sun.prism.shader.AlphaOne_RadialGradient_Loader",
            "com.sun.prism.shader.AlphaTextureDifference_Color_Loader",
            "com.sun.prism.shader.AlphaTextureDifference_ImagePattern_Loader",
            "com.sun.prism.shader.AlphaTextureDifference_LinearGradient_Loader",
            "com.sun.prism.shader.AlphaTextureDifference_RadialGradient_Loader",
            "com.sun.prism.shader.AlphaTexture_Color_Loader",
            "com.sun.prism.shader.AlphaTexture_ImagePattern_Loader",
            "com.sun.prism.shader.AlphaTexture_LinearGradient_Loader",
            "com.sun.prism.shader.AlphaTexture_RadialGradient_Loader",
            "com.sun.prism.shader.DrawCircle_Color_Loader",
            "com.sun.prism.shader.DrawCircle_ImagePattern_Loader",
            "com.sun.prism.shader.DrawCircle_LinearGradient_PAD_Loader",
            "com.sun.prism.shader.DrawCircle_LinearGradient_REFLECT_Loader",
            "com.sun.prism.shader.DrawCircle_LinearGradient_REPEAT_Loader",
            "com.sun.prism.shader.DrawCircle_RadialGradient_PAD_Loader",
            "com.sun.prism.shader.DrawCircle_RadialGradient_REFLECT_Loader",
            "com.sun.prism.shader.DrawCircle_RadialGradient_REPEAT_Loader",
            "com.sun.prism.shader.DrawEllipse_Color_Loader",
            "com.sun.prism.shader.DrawEllipse_ImagePattern_Loader",
            "com.sun.prism.shader.DrawEllipse_LinearGradient_PAD_Loader",
            "com.sun.prism.shader.DrawEllipse_LinearGradient_REFLECT_Loader",
            "com.sun.prism.shader.DrawEllipse_LinearGradient_REPEAT_Loader",
            "com.sun.prism.shader.DrawEllipse_RadialGradient_PAD_Loader",
            "com.sun.prism.shader.DrawEllipse_RadialGradient_REFLECT_Loader",
            "com.sun.prism.shader.DrawEllipse_RadialGradient_REPEAT_Loader",
            "com.sun.prism.shader.DrawPgram_Color_Loader",
            "com.sun.prism.shader.DrawPgram_ImagePattern_Loader",
            "com.sun.prism.shader.DrawPgram_LinearGradient_PAD_Loader",
            "com.sun.prism.shader.DrawPgram_LinearGradient_REFLECT_Loader",
            "com.sun.prism.shader.DrawPgram_LinearGradient_REPEAT_Loader",
            "com.sun.prism.shader.DrawPgram_RadialGradient_PAD_Loader",
            "com.sun.prism.shader.DrawPgram_RadialGradient_REFLECT_Loader",
            "com.sun.prism.shader.DrawPgram_RadialGradient_REPEAT_Loader",
            "com.sun.prism.shader.DrawRoundRect_Color_Loader",
            "com.sun.prism.shader.DrawRoundRect_ImagePattern_Loader",
            "com.sun.prism.shader.DrawRoundRect_LinearGradient_PAD_Loader",
            "com.sun.prism.shader.DrawRoundRect_LinearGradient_REFLECT_Loader",
            "com.sun.prism.shader.DrawRoundRect_LinearGradient_REPEAT_Loader",
            "com.sun.prism.shader.DrawRoundRect_RadialGradient_PAD_Loader",
            "com.sun.prism.shader.DrawRoundRect_RadialGradient_REFLECT_Loader",
            "com.sun.prism.shader.DrawRoundRect_RadialGradient_REPEAT_Loader",
            "com.sun.prism.shader.DrawSemiRoundRect_Color_Loader",
            "com.sun.prism.shader.DrawSemiRoundRect_ImagePattern_Loader",
            "com.sun.prism.shader.DrawSemiRoundRect_LinearGradient_PAD_Loader",
            "com.sun.prism.shader.DrawSemiRoundRect_LinearGradient_REFLECT_Loader",
            "com.sun.prism.shader.DrawSemiRoundRect_LinearGradient_REPEAT_Loader",
            "com.sun.prism.shader.DrawSemiRoundRect_RadialGradient_PAD_Loader",
            "com.sun.prism.shader.DrawSemiRoundRect_RadialGradient_REFLECT_Loader",
            "com.sun.prism.shader.DrawSemiRoundRect_RadialGradient_REPEAT_Loader",
            "com.sun.prism.shader.FillCircle_Color_Loader",
            "com.sun.prism.shader.FillCircle_ImagePattern_Loader",
            "com.sun.prism.shader.FillCircle_LinearGradient_PAD_Loader",
            "com.sun.prism.shader.FillCircle_LinearGradient_REFLECT_Loader",
            "com.sun.prism.shader.FillCircle_LinearGradient_REPEAT_Loader",
            "com.sun.prism.shader.FillCircle_RadialGradient_PAD_Loader",
            "com.sun.prism.shader.FillCircle_RadialGradient_REFLECT_Loader",
            "com.sun.prism.shader.FillCircle_RadialGradient_REPEAT_Loader",
            "com.sun.prism.shader.FillEllipse_Color_Loader",
            "com.sun.prism.shader.FillEllipse_ImagePattern_Loader",
            "com.sun.prism.shader.FillEllipse_LinearGradient_PAD_Loader",
            "com.sun.prism.shader.FillEllipse_LinearGradient_REFLECT_Loader",
            "com.sun.prism.shader.FillEllipse_LinearGradient_REPEAT_Loader",
            "com.sun.prism.shader.FillEllipse_RadialGradient_PAD_Loader",
            "com.sun.prism.shader.FillEllipse_RadialGradient_REFLECT_Loader",
            "com.sun.prism.shader.FillEllipse_RadialGradient_REPEAT_Loader",
            "com.sun.prism.shader.FillPgram_Color_Loader",
            "com.sun.prism.shader.FillPgram_ImagePattern_Loader",
            "com.sun.prism.shader.FillPgram_LinearGradient_PAD_Loader",
            "com.sun.prism.shader.FillPgram_LinearGradient_REFLECT_Loader",
            "com.sun.prism.shader.FillPgram_LinearGradient_REPEAT_Loader",
            "com.sun.prism.shader.FillPgram_RadialGradient_PAD_Loader",
            "com.sun.prism.shader.FillPgram_RadialGradient_REFLECT_Loader",
            "com.sun.prism.shader.FillPgram_RadialGradient_REPEAT_Loader",
            "com.sun.prism.shader.FillRoundRect_Color_Loader",
            "com.sun.prism.shader.FillRoundRect_ImagePattern_Loader",
            "com.sun.prism.shader.FillRoundRect_LinearGradient_PAD_Loader",
            "com.sun.prism.shader.FillRoundRect_LinearGradient_REFLECT_Loader",
            "com.sun.prism.shader.FillRoundRect_LinearGradient_REPEAT_Loader",
            "com.sun.prism.shader.FillRoundRect_RadialGradient_PAD_Loader",
            "com.sun.prism.shader.FillRoundRect_RadialGradient_REFLECT_Loader",
            "com.sun.prism.shader.FillRoundRect_RadialGradient_REPEAT_Loader",
            "com.sun.prism.shader.Mask_TextureRGB_Loader",
            "com.sun.prism.shader.Mask_TextureSuper_Loader",
            "com.sun.prism.shader.Solid_Color_Loader",
            "com.sun.prism.shader.Solid_ImagePattern_Loader",
            "com.sun.prism.shader.Solid_LinearGradient_PAD_Loader",
            "com.sun.prism.shader.Solid_LinearGradient_REFLECT_Loader",
            "com.sun.prism.shader.Solid_LinearGradient_REPEAT_Loader",
            "com.sun.prism.shader.Solid_RadialGradient_PAD_Loader",
            "com.sun.prism.shader.Solid_RadialGradient_REFLECT_Loader",
            "com.sun.prism.shader.Solid_RadialGradient_REPEAT_Loader",
            "com.sun.prism.shader.Solid_TextureFirstPassLCD_Loader",
            "com.sun.prism.shader.Solid_TextureRGB_Loader",
            "com.sun.prism.shader.Solid_TextureSecondPassLCD_Loader",
            "com.sun.prism.shader.Solid_TextureYV12_Loader",
            "com.sun.prism.shader.Texture_Color_Loader",
            "com.sun.prism.shader.Texture_ImagePattern_Loader",
            "com.sun.prism.shader.Texture_LinearGradient_PAD_Loader",
            "com.sun.prism.shader.Texture_LinearGradient_REFLECT_Loader",
            "com.sun.prism.shader.Texture_LinearGradient_REPEAT_Loader",
            "com.sun.prism.shader.Texture_RadialGradient_PAD_Loader",
            "com.sun.prism.shader.Texture_RadialGradient_REFLECT_Loader",
            "com.sun.prism.shader.Texture_RadialGradient_REPEAT_Loader",
            "com.sun.scenario.effect.impl.prism.PrRenderer",
            "com.sun.scenario.effect.impl.prism.ps.PPSRenderer",
            "com.sun.scenario.effect.impl.prism.ps.PPSBlend_SRC_INPeer",
            "com.sun.scenario.effect.impl.prism.ps.PPSLinearConvolvePeer",
            "com.sun.scenario.effect.impl.prism.ps.PPSLinearConvolveShadowPeer",
            "com.sun.xml.internal.stream.XMLInputFactoryImpl",
            "com.sun.glass.ui.EventLoop",
            "com.sun.glass.ui.Application",
            "com.sun.glass.ui.Menu",
            "com.sun.glass.ui.MenuItem$Callback",
            "com.sun.glass.ui.View",
            "com.sun.glass.ui.Size",
            "com.sun.glass.ui.CommonDialogs$ExtensionFilter",
            "com.sun.glass.ui.CommonDialogs$FileChooserResult"
    ));

    private static final List<String> javafxSWReflectionClassList = Arrays.asList(
            "com.sun.prism.sw.SWPipeline",
            "com.sun.prism.sw.SWResourceFactory");

    private static final List<String> javaJNIClassList = Arrays.asList(
            "java.io.File",
            "java.io.FileNotFoundException",
            "java.io.InputStream",
            "java.lang.Boolean",
            "java.lang.Class",
            "java.lang.ClassNotFoundException",
            "java.lang.IllegalStateException",
            "java.lang.Integer",
            "java.lang.Iterable",
            "java.lang.Long",
            "java.lang.Runnable",
            "java.lang.String",
            "java.lang.Thread",
            "java.net.SocketTimeoutException",
            "java.nio.ByteBuffer",
            "java.nio.charset.Charset",
            "java.util.ArrayList",
            "java.util.HashMap",
            "java.util.HashSet",
            "java.util.Iterator",
            "java.util.List",
            "java.util.Map",
            "java.util.Set");

    private static final List<String> javafxJNIClassList = Arrays.asList(
            "com.sun.glass.ui.Application",
            "com.sun.glass.ui.Clipboard",
            "com.sun.glass.ui.Cursor",
            "com.sun.glass.ui.Menu",
            "com.sun.glass.ui.MenuItem$Callback",
            "com.sun.glass.ui.Pixels",
            "com.sun.glass.ui.Screen",
            "com.sun.glass.ui.Size",
            "com.sun.glass.ui.View",
            "com.sun.glass.ui.Window",
            "com.sun.javafx.geom.Path2D",
            "com.sun.glass.ui.CommonDialogs$ExtensionFilter",
            "com.sun.glass.ui.CommonDialogs$FileChooserResult");

    private static final List<String> javafxSWJNIClassList = Arrays.asList(
            "com.sun.pisces.AbstractSurface",
            "com.sun.pisces.JavaSurface",
            "com.sun.pisces.PiscesRenderer",
            "com.sun.pisces.Transform6");

    // Default settings below, can be overridden by subclasses

    String getAdditionalSourceFileLocation() {
        return "/native/linux/";
    }


    List<String> getAdditionalSourceFiles() {
        return defaultAdditionalSourceFiles;
    }

    String getCompiler() {
        return "gcc";
    }

    String getLinker() {
        return "gcc";
    }

    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        return Collections.emptyList();
    }

    List<String> getTargetSpecificCCompileFlags() {
        return Collections.emptyList();
    }

    List<String> getTargetSpecificAOTCompileFlags() {
        return Collections.emptyList();
    }

    List<String> getTargetSpecificObjectFiles() throws IOException {
        return Collections.emptyList();
    }

}
