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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class NativeImage {

    private boolean useJNI = true;

    public void setUseJNI(boolean v) {
        this.useJNI = v;
    }

    public int compile(String graalVMRoot, String classPath, String mainClass) {
        ProcessBuilder pb = new ProcessBuilder();
        List<String> command = pb.command();
        command.add(getNativeImageExecutable(graalVMRoot).toString());
        command.add("-cp");
        command.add(classPath);
        addJNIParameters (command);

        command.add(mainClass);
        int exitStatus = 1;
        try {
            pb.redirectError(new File("/tmp/error"));
            pb.redirectOutput(new File("/tmp/output"));
            Process p = pb.start();
            exitStatus = p.waitFor();
            System.err.println("ExitStatus = "+exitStatus);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            exitStatus = -1;
        }
        return exitStatus;
    }

    // add all parameters required to deal with JNI platform or not
    private void addJNIParameters (List<String> command) {
        if (useJNI) {
            command.add("-Dsvm.platform=org.graalvm.nativeimage.impl.InternalPlatform$LINUX_JNI_AMD64");
            command.add("-H:+ExitAfterRelocatableImageWrite");
        }
    }

    private Path getNativeImageExecutable(String graalVMRoot) {
        return Path.of(graalVMRoot, "bin", "native-image");
    }

}
