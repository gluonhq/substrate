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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DarwinTargetConfiguration extends AbstractTargetConfiguration {

    @Override
    String getAdditionalSourceFileLocation() {
        return "/native/macosx/";
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        return Arrays.asList("AppDelegate.m", "launcher.c", "thread.c");
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        if (!useJavaFX) {
            return Arrays.asList("-Wl,-framework,Foundation", "-Wl,-framework,AppKit");
        }
        String libPath = "-Wl,-force_load," + projectConfiguration.getJavafxStaticLibsPath() + "/";
        List<String> answer = new ArrayList<>(Arrays.asList(
                libPath + "libprism_es2.a", libPath + "libglass.a",
                libPath + "libjavafx_font.a", libPath + "libjavafx_iio.a"));
        answer.addAll(macoslibs);
        if (usePrismSW) {
            answer.add(libPath + "libprism_sw.a");
        }
        return answer;
    }

    private static final List<String> macoslibs = Arrays.asList("-lffi",
            "-lpthread", "-lz", "-ldl", "-lstrictmath", "-llibchelper",
            "-ljava", "-lnio", "-lzip", "-lnet", "-ljvm", "-lobjc",
            "-Wl,-framework,Foundation", "-Wl,-framework,AppKit",
            "-Wl,-framework,ApplicationServices", "-Wl,-framework,OpenGL",
            "-Wl,-framework,QuartzCore", "-Wl,-framework,Security");

    @Override
    List<String> getJavaFXReflectionClassList() {
        List<String> answer = super.getJavaFXReflectionClassList();
        answer.addAll(javafxReflectionMacClassList);
        return answer;
    }

    @Override
    List<String> getJNIClassList(boolean useJavaFX, boolean usePrismSW) {
        List<String> answer = super.getJNIClassList(useJavaFX, usePrismSW);
        if (useJavaFX) answer.addAll(javafxJNIMacClassList);
        return answer;
    }

    private static final List<String> javafxReflectionMacClassList = Arrays.asList(
            "com.sun.prism.es2.ES2Pipeline",
            "com.sun.prism.es2.ES2ResourceFactory",
            "com.sun.prism.es2.ES2Shader",
            "com.sun.prism.es2.MacGLFactory",
            "com.sun.scenario.effect.impl.es2.ES2ShaderSource",
            "com.sun.glass.ui.mac.MacApplication",
            "com.sun.glass.ui.mac.MacView",
            "com.sun.glass.ui.mac.MacPlatformFactory",
            "com.sun.glass.ui.mac.MacGestureSupport",
            "com.sun.glass.ui.mac.MacMenuBarDelegate",
            "com.sun.glass.ui.mac.MacCommonDialogs",
            "com.sun.glass.ui.mac.MacFileNSURL",
            "com.sun.javafx.font.coretext.CTFactory"
    );

    private static final List<String>javafxJNIMacClassList = Arrays.asList(
            "com.sun.glass.ui.mac.MacApplication",
            "com.sun.glass.ui.mac.MacCommonDialogs",
            "com.sun.glass.ui.mac.MacCursor",
            "com.sun.glass.ui.mac.MacGestureSupport",
            "com.sun.glass.ui.mac.MacMenuBarDelegate",
            "com.sun.glass.ui.mac.MacMenuDelegate",
            "com.sun.glass.ui.mac.MacView",
            "com.sun.glass.ui.mac.MacWindow",
            "com.sun.javafx.font.coretext.CGAffineTransform",
            "com.sun.javafx.font.coretext.CGPoint",
            "com.sun.javafx.font.coretext.CGRect",
            "com.sun.javafx.font.coretext.CGSize",
            "com.sun.javafx.font.FontConfigManager$FcCompFont",
            "com.sun.javafx.font.FontConfigManager$FontConfigFont",
            "com.sun.glass.ui.EventLoop"
    );

}
