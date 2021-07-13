/*
 * Copyright (c) 2021, Gluon
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
package com.gluonhq.substrate.feature;

import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import org.graalvm.nativeimage.hosted.Feature;


/**
 *
 * GraalVM feature that deals with adding specific native libraries.
 * For those libs, JNI_OnLoad_"libname" invocations are generated.
 * A list of symbols is added that are marked as "U" in the compiled 
 * objectfile, so that the linker knows which symbols to take from the
 * provided linklibs (as opposed to include the whole linked libs).
 */
// We want this to be working on all platforms, but for now, it is linux-
// supported only, so we include the feature in LinuxTargetConfiguration
// @AutomaticFeature 
public class GluonFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        System.err.println("[GluonFeature] enabled for config " + access);
        return true;
    }
    
    @Override
    public void duringSetup(DuringSetupAccess access) {
        System.err.println("GluonFeature enabled in setup " + access);
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("prism_sw");
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("prism_es2");
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("glass");
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("glassgtk3");

    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        NativeLibraries nativeLibraries = ((BeforeAnalysisAccessImpl) a).getNativeLibraries();
        nativeLibraries.addStaticJniLibrary("prism_es2");
        nativeLibraries.addStaticJniLibrary("glass");
        nativeLibraries.addStaticJniLibrary("glassgtk3");
        
        PlatformNativeLibrarySupport pnls = PlatformNativeLibrarySupport.singleton();
        pnls.addBuiltinPkgNativePrefix("com_sun_javafx_iio_jpeg");
        pnls.addBuiltinPkgNativePrefix("com_sun_javafx_font_FontConfigManager");
        pnls.addBuiltinPkgNativePrefix("com_sun_javafx_font_freetype");
        pnls.addBuiltinPkgNativePrefix("com_sun_prism");
        pnls.addBuiltinPkgNativePrefix("com_sun_glass");
    }

}
