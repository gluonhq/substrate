/*
 * Copyright (c) 2019, Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
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
package com.gluonhq.substrate;

import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.ProjectConfiguration;
import com.gluonhq.substrate.model.Triplet;
import com.gluonhq.substrate.target.DarwinTargetConfiguration;
import com.gluonhq.substrate.target.IosTargetConfiguration;
import com.gluonhq.substrate.target.LinuxTargetConfiguration;
import com.gluonhq.substrate.target.TargetConfiguration;
import com.gluonhq.substrate.util.FileDeps;
import com.gluonhq.substrate.util.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

public class SubstrateDispatcher {

    private static Path omegaPath;
    private static Path gvmPath;

    private static volatile boolean run = true;

    public static void main(String[] args) throws Exception {

        String classPath = requireArg("imagecp","Use -Dimagecp=/path/to/classes");
        String graalVM   = requireArg( "graalvm","Use -Dgraalvm=/path/to/graalvm");
        String mainClass = requireArg( "mainclass", "Use -Dmainclass=main.class.name" );
        String appName   = Optional.ofNullable(System.getProperty("appname")).orElse("anonymousApp");
        String targetProfile = System.getProperty("targetProfile");
        boolean usePrismSW = Boolean.parseBoolean(System.getProperty("prism.sw", "false"));
        boolean skipCompile = Boolean.parseBoolean(System.getProperty("skipcompile", "false"));
        boolean skipSigning = Boolean.parseBoolean(System.getProperty("skipsigning", "false"));
        String expected  = System.getProperty("expected");

        Triplet targetTriplet = targetProfile != null? new Triplet(Constants.Profile.valueOf(targetProfile.toUpperCase()))
                :Triplet.fromCurrentOS();

        ProjectConfiguration config = new ProjectConfiguration();
        config.setGraalPath(graalVM);
        config.setMainClassName(mainClass);
        config.setAppName(appName);
        config.setJavaStaticSdkVersion(Constants.DEFAULT_JAVA_STATIC_SDK_VERSION);
        config.setJavafxStaticSdkVersion(Constants.DEFAULT_JAVAFX_STATIC_SDK_VERSION);
        config.setTarget(targetTriplet);
        config.setUsePrismSW(usePrismSW);
        config.getIosSigningConfiguration().setSkipSigning(skipSigning);

        TargetConfiguration targetConfiguration = getTargetConfiguration(targetTriplet);
        Path buildRoot = Paths.get(System.getProperty("user.dir"), "build", "autoclient");
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        System.err.println("Config: " + config);
        System.err.println("Compiling...");
        System.err.println("ClassPath for compilation = "+classPath);
        Thread timer = new Thread(() -> {
            int counter = 1;
            while (run) {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.err.println("NativeCompile is still running, please hold [" + counter++ + " minute(s)]");
            }
        });
        timer.setDaemon(true);
        timer.start();
        boolean result = nativeCompile(buildRoot, config, classPath);
        run = false;
        if (!result) {
            System.err.println("COMPILE FAILED");
            return;
        }
        try {
            System.err.println("Linking...");
            if (!nativeLink(buildRoot, config)) {
                System.err.println("Linking failed");
                System.exit(1);
            }
        } catch (Throwable t) {
            System.err.println("Linking failed with an exception");
            t.printStackTrace();
            System.exit(1);
        }
        System.err.println("Running...");
        if (expected != null) {
            InputStream is = targetConfiguration.run(paths.getAppPath(), appName);
            // TODO: compare expected and actual output

        } else {
            nativeRun(buildRoot, config);
        }
    }

    private static String requireArg(String argName, String errorMessage ) {
        String arg = System.getProperty(argName);
        if (arg == null || arg.trim().isEmpty()) {
            printUsage();
            throw new IllegalArgumentException( String.format("No '%s' specified. %s", argName, errorMessage));
        }
        return arg;
    }

    private static void printUsage() {
        System.err.println("Usage:\n java -Dimagecp=... -Dgraalvm=... -Dmainclass=... com.gluonhq.substrate.SubstrateDispatcher");
    }

    /**
     * This method will start native compilation for the specified configuration. The classpath and the buildroot need
     * to be provided separately.
     * The result of compilation is a at least one native file (2 files in case LLVM backend is used).
     * This method returns <code>true</code> on successful compilation and <code>false</code> when compilations fails
     * @param buildRoot the root, relative to which the compilation step can create objectfiles and temporary files
     * @param config the ProjectConfiguration, including the target triplet
     * @param classPath the classpath needed to compile the application (this is not the classpath for native-image)
     * @return true if compilation succeeded, false if it fails
     * @throws Exception
     * @throws IllegalArgumentException when the supplied configuration contains illegal combinations
     */
    public static boolean nativeCompile(Path buildRoot, ProjectConfiguration config, String classPath) throws Exception {
        boolean useJavaFX = Stream.of(classPath.split(File.pathSeparator))
                .anyMatch(s -> s.contains("javafx"));
        config.setUseJavaFX(useJavaFX);

        Triplet targetTriplet  = config.getTargetTriplet();
        if (! canCompileTo(config.getHostTriplet(), config.getTargetTriplet())) {
            throw new IllegalArgumentException("We currently can't compile to "+targetTriplet+" when running on "+config.getHostTriplet());
        }
        TargetConfiguration targetConfiguration = getTargetConfiguration(targetTriplet);
        if (targetConfiguration == null) {
            throw new IllegalArgumentException("We don't have a configuration to compile "+targetTriplet);
        }
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        Logger.logInit(paths.getLogPath().toString(), "==================== COMPILE TASK ====================",
                config.isVerbose());
        System.err.println("We will now compile your code for "+targetTriplet.toString()+". This may take some time.");
        boolean compile = targetConfiguration.compile(paths, config, classPath);
        if (compile) {
            System.err.println("Compilation succeeded.");
        } else {
            System.err.println("Compilation failed. The error should be printed above.");
        }
        return compile;
    }

    public static boolean nativeLink(Path buildRoot, ProjectConfiguration config) throws IOException, InterruptedException {
        Triplet targetTriplet  = config.getTargetTriplet();
        TargetConfiguration targetConfiguration = getTargetConfiguration(targetTriplet);
        if (targetConfiguration == null) {
            throw new IllegalArgumentException("We don't have a configuration to compile "+targetTriplet);
        }
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        FileDeps.setupDependencies(config);
        boolean link = targetConfiguration.link(paths, config);
        return link;
    }

    public static void nativeRun(Path buildRoot, ProjectConfiguration config) throws IOException, InterruptedException {
        Triplet targetTriplet  = config.getTargetTriplet();
        TargetConfiguration targetConfiguration = getTargetConfiguration(targetTriplet);
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        targetConfiguration.runUntilEnd(paths, config);
    }

    private static TargetConfiguration getTargetConfiguration(Triplet targetTriplet) {
        switch( targetTriplet.getOs() ) {
            case Constants.OS_LINUX : return new LinuxTargetConfiguration();
            case Constants.OS_DARWIN: return new DarwinTargetConfiguration();
            case Constants.OS_IOS: return new IosTargetConfiguration();
            default: return null;
        }
    }

    /*
     * check if this host can be used to provide binaries for this target.
     * host and target should not be null.
     */
    private static boolean canCompileTo(Triplet host, Triplet target) {
        // if the host os and target os are the same, always return true
        if (host.getOs().equals(target.getOs())) return true;
        // if host is linux and target is ios, fail
        if (Constants.OS_LINUX == host.getOs()) {
            if (Constants.OS_IOS == target.getOs()) return false;
        }
        return true;
    }

    private static String prepareDirs(Path buildRoot) throws IOException {

        omegaPath = buildRoot != null? buildRoot : Paths.get(System.getProperty("user.dir"),"build", "client");
        String rootDir = omegaPath.toAbsolutePath().toString();

        gvmPath = Paths.get(rootDir, "gvm");
        gvmPath = Files.createDirectories(gvmPath);
        return  gvmPath.toAbsolutePath().toString();

    }

}
