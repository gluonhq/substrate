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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AndroidTargetConfiguration extends AbstractTargetConfiguration {

    private final String ndk;
    private final Path ldlld;
    private final Path clang;

    private List<String> androidAdditionalSourceFiles = Arrays.asList("StrictMath.c", "JvmFuncs.c", "launcher.c");
    private List<String> androidAdditionalHeaderFiles = Arrays.asList("fdlibm.h", "jfdlibm.h", "grandroid.h");
    private List<String> cFlags = Arrays.asList("-target", "aarch64-linux-android", "-I.");
    private List<String> linkFlags = Arrays.asList("-target", "aarch64-linux-android21", "-fPIC", "-Wl,--gc-sections", "-shared");

    public AndroidTargetConfiguration() {
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

    @Override
    public boolean compile(ProcessPaths paths, ProjectConfiguration config, String classPath) throws IOException, InterruptedException {
        // we override compile as we need to do some checks first. If we have no ld.lld in android_ndk, we should not start compiling
        if (ndk == null) throw new IOException ("Can't find an Android NDK on your system. Set the environment property ANDROID_NDK");
        if (ldlld == null) throw new IOException ("You specified an android ndk, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/linux-x86_64/bin/ldlld");
        if (clang == null) throw new IOException ("You specified an android ndk, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/linux-x86_64/bin/clang");
        return super.compile(paths, config, classPath);
    }

    @Override
    public boolean link(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {
        // we override compile as we need to do some checks first. If we have no clang in android_ndk, we should not start linking
        if (ndk == null) throw new IOException ("Can't find an Android NDK on your system. Set the environment property ANDROID_NDK");
        if (clang == null) throw new IOException ("You specified an android ndk, but it doesn't contain "+ndk+"/toolchains/llvm/prebuilt/linux-x86_64/bin/clang");
        return super.link(paths, projectConfiguration);
    }

    @Override
    List<String> getTargetSpecificAOTCompileFlags() throws IOException {
        Path llcPath = getLlcPath();
        return Arrays.asList("-H:CompilerBackend=" + Constants.BACKEND_LLVM,
                "-H:-SpawnIsolates",
                "-Dsvm.targetArch=" + projectConfiguration.getTargetTriplet().getArch(),
                "-H:CustomLD=" + ldlld.toAbsolutePath().toString(),
                "-H:CustomLLC=" + llcPath.toAbsolutePath().toString());
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
