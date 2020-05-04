/*
 * Copyright (c) 2019, 2020, Gluon
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

import java.nio.file.Path;

public class Constants {

    public static final String GLUON_SUBSTRATE = "GluonSubstrate";
    public static final Path USER_SUBSTRATE_PATH = Path.of(System.getProperty("user.home"))
            .resolve(".gluon").resolve("substrate");

    /**
     * Triplet architecture
     */
    public static final String ARCH_AMD64 = "x86_64";
    public static final String ARCH_ARM64 = "arm64";
    public static final String ARCH_AARCH64 = "aarch64";

    /**
     * Triplet vendor
     */
    public static final String VENDOR_APPLE = "apple";
    public static final String VENDOR_LINUX = "linux";
    public static final String VENDOR_MICROSOFT = "microsoft";

    /**
     * Triplet OS
     */
    public static final String OS_DARWIN = "darwin";
    public static final String OS_IOS = "ios";
    public static final String OS_LINUX = "linux";
    public static final String OS_WINDOWS = "windows";
    public static final String OS_ANDROID = "android";

    /**
     * Predefined Profiles
     */
    public enum Profile {
        LINUX, // (x86_64-linux-linux)
        LINUX_AARCH64, // (aarch64-linux-linux or aarch64-linux-gnu)
        MACOS, // (x86_64-apple-darwin)
        WINDOWS, // (x86_64-windows-windows)
        IOS,   // (aarch64-apple-ios)
        IOS_SIM,   // (x86_64-apple-ios)
        ANDROID // (aarch64-linux-android);
    };

    /**
     * Supported hosts
     *
     */
    public static final String HOST_MAC = "macos";
    public static final String HOST_LINUX = "linux";
    public static final String HOST_WINDOWS = "windows";

    /**
     * Supported targets
     *
     */
    @Deprecated public static final String TARGET_HOST = "host"; // either mac or linux, based on host
    @Deprecated public static final String TARGET_MAC = "macos";
    @Deprecated public static final String TARGET_LINUX = "linux";
    @Deprecated public static final String TARGET_IOS = "ios";
    @Deprecated public static final String TARGET_IOS_SIM = "ios-sim";

    /**
     * Supported profiles
     *
     */
    public static final String PROFILE_HOST = "host"; // either mac or linux, based on host
    public static final String PROFILE_MAC = "macos";
    public static final String PROFILE_LINUX = "linux";
    public static final String PROFILE_IOS = "ios";
    public static final String PROFILE_IOS_SIM = "ios-sim";
    public static final String PROFILE_ANDROID = "android";
    public static final String PROFILE_LINUX_AARCH64 = "linux-aarch64";

    public static final String DEFAULT_JAVA_STATIC_SDK_VERSION  = "15-ea+3";
    public static final String DEFAULT_JAVAFX_STATIC_SDK_VERSION  = "15-ea+gvm17";

    /**
     * Paths
     */
    public static final String CLIENT_PATH = "client";
    public static final String GVM_PATH = "gvm";
    public static final String GEN_PATH = "gensrc";
    public static final String SOURCE_PATH = "src";
    public static final String TMP_PATH = "tmp";
    public static final String LIB_PATH = "lib";
    public static final String LOG_PATH = "log";
    public static final String APK_PATH = "apk";
    public static final String NATIVE_CODE_PATH = "native";



    /**
     * Backend
     */
    public static final String BACKEND_LIR = "lir";
    public static final String BACKEND_LLVM = "llvm";

    /**
     * Supported files
     */
    public static final String PLIST_FILE = "Default-Info.plist";
    public static final String IOS_ASSETS_FOLDER = "assets";
    public static final String MANIFEST_FILE = "AndroidManifest.xml";
    public static final String ANDROID_RES_FOLDER = "res";
    public static final String ANDROID_KEYSTORE = "debug.keystore";

    public static final String META_INF_SUBSTRATE_DALVIK = "META-INF/substrate/dalvik";
    public static final String DALVIK_PRECOMPILED_CLASSES = "/precompiled/classes/";
    public static final String DALVIK_ACTIVITY_PACKAGE = "com/gluonhq/helloandroid/";
    public static final String DALVIK_JAVAFX_PACKAGE = "javafx/scene/input/";

    public static final String META_INF_SUBSTRATE_CONFIG = "META-INF/substrate/config/";
    public static final String USER_INIT_BUILD_TIME_FILE = "initbuildtime";
    public static final String USER_INIT_BUILD_TIME_ARCHOS_FILE = "initbuildtime-${archOs}";
    public static final String USER_REFLECTION_FILE = "reflectionconfig.json";
    public static final String USER_REFLECTION_ARCHOS_FILE = "reflectionconfig-${archOs}.json";
    public static final String USER_JNI_FILE = "jniconfig.json";
    public static final String USER_JNI_ARCHOS_FILE = "jniconfig-${archOs}.json";
    public static final String USER_RESOURCE_FILE = "resourceconfig.json";
    public static final String USER_RESOURCE_ARCHOS_FILE = "resourceconfig-${archOs}.json";

    public static final String CONFIG_FILES = "/config/";
    public static final String REFLECTION_JAVA_FILE = "reflectionconfig-java.json";
    public static final String REFLECTION_JAVAFXSW_FILE = "reflectionconfig-javafxsw.json";
    public static final String REFLECTION_ARCH_FILE = "reflectionconfig-${archOs}.json";

    public static final String RESOURCE_ARCH_FILE = "resourceconfig-${archOs}.json";

    public static final String JNI_JAVA_FILE = "jniconfig-java.json";
    public static final String JNI_JAVAFXSW_FILE = "jniconfig-javafxsw.json";
    public static final String JNI_ARCH_FILE = "jniconfig-${archOs}.json";
}
