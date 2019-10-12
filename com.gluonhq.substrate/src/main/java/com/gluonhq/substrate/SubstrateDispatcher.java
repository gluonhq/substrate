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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class SubstrateDispatcher {

    private static Path omegaPath;
    private static Path gvmPath;

    public static void main(String[] args) throws Exception {
        String classPath = System.getProperty("imagecp");
        String graalVM = System.getProperty("graalvm");
        String mainClass = System.getProperty("mainclass");
        String appName = System.getProperty("appname");
        String expected = System.getProperty("expected");
        if (classPath == null || classPath.isEmpty()) {
            printUsage();
            throw new IllegalArgumentException("No classpath specified. Use -Dimagecp=/path/to/classes");
        }
        if (graalVM == null || graalVM.isEmpty()) {
            printUsage();
            throw new IllegalArgumentException("No graalvm specified. Use -Dgraalvm=/path/to/graalvm");
        }
        if (mainClass == null || mainClass.isEmpty()) {
            printUsage();
            throw new IllegalArgumentException("No mainclass specified. Use -Dmainclass=main.class.name");
        }
        if (appName == null) {
            appName = "anonymousApp";
        }
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
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
        ProcessPaths paths = new ProcessPaths(buildRoot.toString(), targetTriplet.getArchOs());
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

    static void printUsage() {
        System.err.println("Usage:\n java -Dimagecp=... -Dgraalvm=... -Dmainclass=... com.gluonhq.substrate.SubstrateDispatcher");
    }

    public static void nativeCompile(String buildRoot, ProjectConfiguration config, String classPath) throws Exception {
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
    public static void nativeLink(String buildRoot, ProjectConfiguration config) throws IOException, InterruptedException {
        Triplet targetTriplet  = config.getTargetTriplet();
        TargetConfiguration targetConfiguration = getTargetConfiguration(targetTriplet);
        if (targetConfiguration == null) {
            throw new IllegalArgumentException("We don't have a configuration to compile "+targetTriplet);
        }
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        FileDeps.setupDependencies(config);
        targetConfiguration.link(paths, config);
    }

    public static void nativeRun(String buildRoot, ProjectConfiguration config) throws IOException, InterruptedException {
        Triplet targetTriplet  = config.getTargetTriplet();
        TargetConfiguration targetConfiguration = getTargetConfiguration(targetTriplet);
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        targetConfiguration.runUntilEnd(paths.getAppPath(), config.getAppName());
    }

    private static TargetConfiguration getTargetConfiguration(Triplet targetTriplet) {
        if (Constants.OS_LINUX == targetTriplet.getOs()) {
            return new LinuxTargetConfiguration();
        }
        if (Constants.OS_DARWIN == targetTriplet.getOs()) {
            return new DarwinTargetConfiguration();

        }
        return null;
    }

    private static String prepareDirs(String buildRoot) throws IOException {
        String gvmDir = null;
        omegaPath = buildRoot != null && !buildRoot.isEmpty() ?
                Paths.get(buildRoot) : Paths.get(System.getProperty("user.dir")).resolve("build").resolve("client");
        String rootDir = omegaPath.toAbsolutePath().toString();

        gvmPath = Paths.get(rootDir, "gvm");
        gvmPath = Files.createDirectories(gvmPath);
        gvmDir = gvmPath.toAbsolutePath().toString();

        return gvmDir;
    }

}
