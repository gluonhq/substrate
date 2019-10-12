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

import java.nio.file.Path;

public class Constants {

    public static final Path USER_SUBSTRATE_PATH = Path.of(System.getProperty("user.home"))
            .resolve(".gluon").resolve("substrate");

    /**
     * Triplet architecture
     */
    public static final String ARCH_AMD64 = "x86_64";
    public static final String ARCH_ARM64 = "arm64";

    /**
     * Triplet vendor
     */
    public static final String VENDOR_APPLE = "apple";
    public static final String VENDOR_LINUX = "linux";

    /**
     * Triplet OS
     */
    public static final String OS_DARWIN = "darwin";
    public static final String OS_IOS = "ios";
    public static final String OS_LINUX = "linux";

    /**
     * Predefined Profiles
     */
    public enum Profile {
        LINUX, // (x86_64-linux-linux)
        MACOS; // (x86_64-apple-darwin)
    };

    /**
     * Supported hosts
     *
     */
    public static final String HOST_MAC = "macos";
    public static final String HOST_LINUX = "linux";

    /**
     * Supported targets
     *
     */
    public static final String TARGET_HOST = "host"; // either mac or linux, based on host
    public static final String TARGET_MAC = "macos";
    public static final String TARGET_LINUX = "linux";
    public static final String TARGET_IOS = "ios";
    public static final String TARGET_IOS_SIM = "ios-sim";

    public static final String DEFAULT_JAVA_STATIC_SDK_VERSION  = "11-ea+7";

//    /**
//     * Supported target app folders
//     *
//     */
//    public static final String APP_MAC = "mac";
//    public static final String APP_LINUX = "linux";
//    public static final String APP_IOS = "ios";
//
//    /**
//     * Supported target source folders
//     *
//     */
//    public static final String SOURCE_MAC = "mac";
//    public static final String SOURCE_IOS = "ios";

//    /**
//     * String used to download dependencies for supported hosts
//     *
//     */
//    public static final String DEPS_HOST_MAC = "darwin";
//    public static final String DEPS_HOST_LINUX = "linux";
//
//    /**
//     * String used to download dependencies for supported targets
//     *
//     */
//    public static final String DEPS_TARGET_MAC = "macosx";
//    public static final String DEPS_TARGET_LINUX = "linux";
//    public static final String DEPS_TARGET_IOS = "ios";


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



    /**
     * Backend
     */
    public static final String BACKEND_LIR = "lir";
    public static final String BACKEND_LLVM = "llvm";

    /**
     * Supported files
     */
    // public static final String PLIST_FILE = "Default-Info.plist";
}
