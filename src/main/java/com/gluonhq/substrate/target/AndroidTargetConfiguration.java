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
import com.gluonhq.substrate.model.ClassPath;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.util.FileOps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AndroidTargetConfiguration extends PosixTargetConfiguration {

    private final String ndk;
    private final Path ldlld;
    private final Path clang;

    private List<String> androidAdditionalSourceFiles = Collections.singletonList("launcher.c");
    private List<String> androidAdditionalHeaderFiles = Collections.singletonList("grandroid.h");
    private List<String> cFlags = Arrays.asList("-target", "aarch64-linux-android", "-I.");
    private List<String> linkFlags = Arrays.asList("-target", "aarch64-linux-android21", "-fPIC", "-Wl,--gc-sections",
            "-landroid", "-llog", "-lnet", "-shared");
    private List<String> javafxLinkFlags = Arrays.asList("-Wl,--whole-archive",
            "-lprism_es2_monocle", "-lglass_monocle", "-ljavafx_font_freetype", "-Wl,--no-whole-archive",
            "-lGLESv2", "-lEGL", "-lfreetype");


    public AndroidTargetConfiguration( ProcessPaths paths, InternalProjectConfiguration configuration ) {
        super(paths,configuration);
        // for now, we need to have an ANDROID_NDK
        // we will fail fast whenever a method is invoked that uses it (e.g. compile)
        String sysndk = System.getenv("ANDROID_NDK");
        if (sysndk != null) {
            this.ndk = sysndk;
            Path ldguess = Paths.get(this.ndk, "toolchains", "llvm", "prebuilt", "linux-x86_64", "bin", "ld.lld");
            if (Files.exists(ldguess)) {
                ldlld = ldguess;
            } else {
                ldlld = null;
            }
            Path clangguess = Paths.get(this.ndk, "toolchains", "llvm", "prebuilt", "linux-x86_64", "bin", "clang");
            clang = Files.exists(clangguess) ? clangguess : null;
        } else {
            this.ndk = null;
            this.ldlld = null;
            this.clang = null;
        }
    }

    /**
     * // TODO: this is 100% similar to what we do on iOS. We need something like CrossPlatformTools for this.
     * If we are not using JavaFX, we immediately return the provided classpath, no further processing needed
     * If we use JavaFX, we will first obtain the location of the JavaFX SDK for this configuration.
     * This may throw an IOException.
     * After the path to the JavaFX SDK is obtained, the JavaFX jars for the host platform are replaced by
     * the JavaFX jars for the target platform.
     * @param classPath The provided classpath
     * @return A string with the modified classpath if JavaFX is used
     * @throws IOException
     */
    @Override
    String processClassPath(String classPath) throws IOException {
        if (!projectConfiguration.isUseJavaFX()) {
            return classPath;
        }

        return new ClassPath(classPath).mapWithLibs(
                fileDeps.getJavaFXSDKLibsPath(), "javafx-graphics", "javafx-base", "javafx-controls" );

    }

    @Override
    public boolean compile(String classPath) throws IOException, InterruptedException {
        // we override compile as we need to do some checks first. If we have no ld.lld in android_ndk, we should not start compiling
        if (ndk == null) throw new IOException ("Can't find an Android NDK on your system. Set the environment property ANDROID_NDK");
        if (ldlld == null) throw new IOException ("You specified an android ndk, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/linux-x86_64/bin/ldlld");
        if (clang == null) throw new IOException ("You specified an android ndk, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/linux-x86_64/bin/clang");
        return super.compile(classPath);
    }

    @Override
    public boolean link() throws IOException, InterruptedException {
        // we override compile as we need to do some checks first. If we have no clang in android_ndk, we should not start linking
        if (ndk == null) throw new IOException ("Can't find an Android NDK on your system. Set the environment property ANDROID_NDK");
        if (clang == null) throw new IOException ("You specified an android ndk, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/linux-x86_64/bin/clang");
        return super.link();
    }

    @Override
    boolean allowHttps() {
        return false;
    }

    @Override
    List<String> getTargetSpecificAOTCompileFlags() throws IOException {
        Path llcPath = getLlcPath();
        return Arrays.asList("-H:CompilerBackend=" + Constants.BACKEND_LLVM,
                "-H:-SpawnIsolates",
                "-Dsvm.targetArch=" + projectConfiguration.getTargetTriplet().getArch(),
                "-H:+UseOnlyWritableBootImageHeap",
                "-H:CustomLD=" + ldlld.toAbsolutePath().toString(),
                "-H:CustomLLC=" + llcPath.toAbsolutePath().toString());
    }

    @Override
    List<String> getTargetSpecificObjectFiles() throws IOException {
       return FileOps.findFile( paths.getGvmPath(), "llvm.o").map( objectFile ->
           Collections.singletonList(objectFile.toAbsolutePath().toString())
       ).orElseThrow();
    }

    @Override
    public String getCompiler() {
        return clang.toAbsolutePath().toString();
    }

    @Override
    String getLinker() {
        return clang.toAbsolutePath().toString();
    }

    @Override
    List<String> getTargetSpecificCCompileFlags() {
        return cFlags;
    }


    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        if (!useJavaFX) return linkFlags;
        List<String> answer = new ArrayList<>();
        answer.addAll(linkFlags);
        answer.addAll(javafxLinkFlags);
        return answer;
    }

    @Override
    List<String> getTargetSpecificNativeLibsFlags(Path libPath, List<String> libs) {
        List<String> linkFlags = new ArrayList<>();
        linkFlags.add("-Wl,--whole-archive");
        linkFlags.addAll(libs.stream()
                .map(s -> libPath.resolve(s).toString())
                .collect(Collectors.toList()));
        linkFlags.add("-Wl,--no-whole-archive");
        return linkFlags;
    }

    @Override
    public String getAdditionalSourceFileLocation() {
        return "/native/android/c/";
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        return androidAdditionalSourceFiles;
    }


    List<String> getAdditionalHeaderFiles() {
        return androidAdditionalHeaderFiles;
    }
}
