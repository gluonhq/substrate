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
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.XcodeUtils;
import com.gluonhq.substrate.util.ios.CodeSigning;
import com.gluonhq.substrate.util.ios.InfoPlist;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class IosTargetConfiguration extends AbstractTargetConfiguration {

    private List<String> iosAdditionalSourceFiles = Arrays.asList("AppDelegate.m");

    @Override
    public boolean runUntilEnd(Path workDir, String appName) throws IOException, InterruptedException {
        return false;
    }

    @Override
    List<String> getTargetSpecificLinkFlags() {
        return Arrays.asList("-arch", "arm64",
                "-mios-version-min=11.0",
                "-isysroot", getSysroot(),
                "-Wl,-framework,Foundation",
                "-Wl,-framework,UIKit");
    }

    @Override
    List<String> getTargetSpecificCCompileFlags() {
        return Arrays.asList("-xobjective-c",
                "-arch", "arm64",
                "-Dsvn.targetArch=arm64",
                "-isysroot", getSysroot());
    }

    @Override
    List<String> getTargetSpecificAOTCompileFlags() {
        Path llcPath = Path.of(projectConfiguration.getGraalPath(),"bin", "llc");
        return Arrays.asList("-H:CompilerBackend=llvm",
                "-H:-SpawnIsolates",
                "-Dsvm.targetArch=arm64",
                "-H:CustomLLC=" + llcPath.toAbsolutePath().toString());
    }

    @Override
    public String getAdditionalSourceFileLocation() {
        return "/native/ios/";
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        return iosAdditionalSourceFiles;
    }

    List<String> getTargetSpecificObjectFiles() throws IOException {
        Path gvmPath = paths.getGvmPath();
        Path objectFile = FileOps.findFile(gvmPath, "llvm.o");
        return Arrays.asList(objectFile.toAbsolutePath().toString());
    }

    @Override
    public boolean link(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {
        boolean result = super.link(paths, projectConfiguration);

        if (result) {
            createInfoPlist(paths, projectConfiguration);

            if (! isSimulator()) {
                CodeSigning codeSigning = new CodeSigning(paths, projectConfiguration);
                if (! codeSigning.signApp()) {
                    Logger.logSevere("Error signing the app");
                }
            }
        }
        return result;
    }

    @Override
    public String getCompiler() {
        return "clang";
    }

    private String getSysroot() {
        // TODO: Sim?
        return XcodeUtils.SDKS.IPHONEOS.getSDKPath();
    }

    private boolean isSimulator() {
        // TODO
        return false; // Constants.ARCH_AMD64.equals(arch);
    }

    private void createInfoPlist(ProcessPaths paths, ProjectConfiguration projectConfiguration) {
        try {
            InfoPlist infoPlist = new InfoPlist(paths, projectConfiguration, isSimulator() ?
                    XcodeUtils.SDKS.IPHONESIMULATOR : XcodeUtils.SDKS.IPHONEOS);
            Path plist = infoPlist.processInfoPlist();
            if (plist != null) {
                Logger.logDebug("Plist at " + plist.toString());
                FileOps.copyStream(new FileInputStream(plist.toFile()),
                        paths.getAppPath().resolve(projectConfiguration.getAppName() + ".app").resolve(Constants.PLIST_FILE));
            }
        } catch (IOException e) {
            Logger.logSevere("Error creating info.plist");
        }
    }
}
