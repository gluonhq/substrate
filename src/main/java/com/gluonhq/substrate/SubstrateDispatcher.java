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
import com.gluonhq.substrate.target.LinuxTargetConfiguration;
import com.gluonhq.substrate.target.TargetConfiguration;
import com.gluonhq.substrate.util.FileDeps;
import com.gluonhq.substrate.util.Logger;

 import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

public class SubstrateDispatcher {

    private static Path omegaPath;
    private static Path gvmPath;

    public static void main(String[] args) throws Exception {

        String classPath = requireArg("imagecp","Use -Dimagecp=/path/to/classes");
        String graalVM   = requireArg( "graalvm","Use -Dgraalvm=/path/to/graalvm");
        String mainClass = requireArg( "mainclass", "Use -Dmainclass=main.class.name" );
        String appName   = Optional.ofNullable(System.getProperty("appname")).orElse("anonymousApp");
        String expected  = System.getProperty("expected");
        String osName    = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        Triplet targetTriplet;
        if (osName.contains("mac")) {
            targetTriplet  = new Triplet(Constants.Profile.MACOS);
        } else if (osName.contains("nux")) {
            targetTriplet = new Triplet(Constants.Profile.LINUX);
        } else {
            throw new RuntimeException("OS " + osName + " not supported");
        }

        ProjectConfiguration config = new ProjectConfiguration();
        config.setGraalPath(graalVM);
        config.setMainClassName(mainClass);
        config.setAppName(appName);
        config.setJavaStaticSdkVersion(Constants.DEFAULT_JAVA_STATIC_SDK_VERSION);
        config.setTarget(targetTriplet);

        TargetConfiguration targetConfiguration = getTargetConfiguration(targetTriplet);
        Path buildRoot = Paths.get(System.getProperty("user.dir"), "build", "autoclient");
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        System.err.println("Config: " + config);
        System.err.println("Compiling...");
        boolean compile = targetConfiguration.compile(paths, config, classPath);
        if (!compile) {
            System.err.println("COMPILE FAILED");
            return;
        }
        FileDeps.setupDependencies(config);
        System.err.println("Linking...");
        boolean linked = targetConfiguration.link(paths, config);
        if (!linked) {
            System.err.println("Linking failed");
            System.exit(1);
        }
        System.err.println("Running...");
        if (expected != null) {
            InputStream is = targetConfiguration.run(paths.getAppPath(), appName);
            // TODO: compare expected and actual output

        } else {
            targetConfiguration.runUntilEnd(paths.getAppPath(), appName);
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

    public static void nativeCompile(Path buildRoot, ProjectConfiguration config, String classPath) throws Exception {
        Triplet targetTriplet  = config.getTargetTriplet();
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
    }

    public static void nativeLink(Path buildRoot, ProjectConfiguration config) throws IOException, InterruptedException {
        Triplet targetTriplet  = config.getTargetTriplet();
        TargetConfiguration targetConfiguration = getTargetConfiguration(targetTriplet);
        if (targetConfiguration == null) {
            throw new IllegalArgumentException("We don't have a configuration to compile "+targetTriplet);
        }
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        FileDeps.setupDependencies(config);
        targetConfiguration.link(paths, config);
    }

    public static void nativeRun(Path buildRoot, ProjectConfiguration config) throws IOException, InterruptedException {
        Triplet targetTriplet  = config.getTargetTriplet();
        TargetConfiguration targetConfiguration = getTargetConfiguration(targetTriplet);
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        targetConfiguration.runUntilEnd(paths.getAppPath(), config.getAppName());
    }

    private static TargetConfiguration getTargetConfiguration(Triplet targetTriplet) {
        switch( targetTriplet.getOs() ) {
            case Constants.OS_LINUX : return new LinuxTargetConfiguration();
            case Constants.OS_DARWIN: return new DarwinTargetConfiguration();
            default: return null;
        }
    }

    private static String prepareDirs(Path buildRoot) throws IOException {

        omegaPath = buildRoot != null? buildRoot : Paths.get(System.getProperty("user.dir"),"build", "client");
        String rootDir = omegaPath.toAbsolutePath().toString();

        gvmPath = Paths.get(rootDir, "gvm");
        gvmPath = Files.createDirectories(gvmPath);
        return  gvmPath.toAbsolutePath().toString();

    }

}
