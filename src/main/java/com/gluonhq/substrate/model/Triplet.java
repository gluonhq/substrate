/*
 * Copyright (c) 2019, 2021, Gluon
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
package com.gluonhq.substrate.model;

import com.gluonhq.substrate.Constants;

import java.util.Locale;

import static com.gluonhq.substrate.Constants.*;

public class Triplet {

    /**
     * The operating system of the host is evaluated at build-time
     */
    private static final String OS_NAME  = System.getProperty("os.name").toLowerCase(Locale.ROOT);

    private String arch;
    private String vendor;
    private String os;

    /**
     * Creates a new triplet for the current runtime
     * @return the triplet for the current runtime
     * @throws IllegalArgumentException in case the current operating system is not supported
     */
    public static Triplet fromCurrentOS() throws IllegalArgumentException {
        if (isMacOSHost()) {
           return new Triplet(Constants.Profile.MACOS);
        } else if (isLinuxHost()) {
            return new Triplet(Constants.Profile.LINUX);
        } else if (isWindowsHost()) {
            return new Triplet(Constants.Profile.WINDOWS);
        } else {
           throw new IllegalArgumentException("OS " + OS_NAME + " not supported");
        }
    }

    /**
     * @return true if host is Windows
     */
    public static boolean isWindowsHost() {
        return OS_NAME.contains("windows");
    }

    /**
     * @return true if host is MacOS
     */
    public static boolean isMacOSHost() {
        return OS_NAME.contains("mac");
    }

    /**
     * @return true if host is Linux
     */
    public static boolean isLinuxHost() {
        return OS_NAME.contains("nux");
    }

    public Triplet(String arch, String vendor, String os) {
        this.arch = arch;
        this.vendor = vendor;
        this.os = os;
    }

    public Triplet(Constants.Profile profile) {
        switch (profile) {
            case LINUX:
                this.arch = ARCH_AMD64;
                this.vendor = VENDOR_LINUX;
                this.os = OS_LINUX;
                break;
            case LINUX_AARCH64:
                this.arch = ARCH_AARCH64;
                this.vendor = VENDOR_LINUX;
                this.os = OS_LINUX;
                break;
            case MACOS:
                this.arch = ARCH_AMD64;
                this.vendor = VENDOR_APPLE;
                this.os = OS_DARWIN;
                break;
            case WINDOWS:
                this.arch = ARCH_AMD64;
                this.vendor = VENDOR_MICROSOFT;
                this.os = OS_WINDOWS;
                break;
            case IOS:
                this.arch = ARCH_ARM64;
                this.vendor = VENDOR_APPLE;
                this.os = OS_IOS;
                break;
            case IOS_SIM:
                this.arch = ARCH_AMD64;
                this.vendor = VENDOR_APPLE;
                this.os = OS_IOS;
                break;
            case ANDROID:
                this.arch = ARCH_AARCH64;
                this.vendor = VENDOR_LINUX;
                this.os = OS_ANDROID;
                break;
            case WEB:
                this.arch = ARCH_AMD64;
                this.vendor = VENDOR_WEB;
                this.os = OS_WEB;
                break;
            default:
                throw new IllegalArgumentException("Triplet for profile "+profile+" is not supported yet");
        }
    }

    /*
     * check if this host can be used to provide binaries for this target.
     * host and target should not be null.
     */
    public boolean canCompileTo(Triplet target) {
        // if the host os and target os are the same, always return true
        if (getOs().equals(target.getOs())) return true;

        // so far, iOS can be built from Mac, Android can be built from Linux
        return (OS_DARWIN.equals(getOs()) && OS_IOS.equals(target.getOs())) ||
                (OS_LINUX.equals(getOs()) && OS_ANDROID.equals(target.getOs()));
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getArchOs() {
        return this.arch+"-"+this.os;
    }

    public String getOsArch() {
        return this.os+"-"+this.arch;
    }

    /**
     * returns os-arch but use amd64 instead of x86_64.
     * This should become the default
     * @return
     */
    public String getOsArch2() {
        String myarch = this.arch;
        if (myarch.equals("x86_64")) {
            myarch = "amd64";
        }
        return this.os+"-"+myarch;
    }

    /**
     *
     * On iOS/iOS-sim and Android returns a string with a valid version for clibs,
     * for other OSes returns an empty string
     * @return
     */
    public String getClibsVersion() {
        if (OS_IOS.equals(getOs()) || OS_ANDROID.equals(getOs())) {
            return "-ea+" + Constants.DEFAULT_CLIBS_VERSION;
        }
        return "";
    }
    /**
     *
     * On iOS/iOS-sim and Android returns a string with a valid path for clibs,
     * for other OSes returns an empty string
     * @return
     */
    public String getClibsVersionPath() {
        if (OS_IOS.equals(getOs()) || OS_ANDROID.equals(getOs())) {
            return Constants.DEFAULT_CLIBS_VERSION;
        }
        return "";
    }

    @Override
    public String toString() {
        return arch + '-' + vendor + '-' + os;
    }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof Triplet)) return false;
        Triplet target = (Triplet)o;
        return (this.arch.equals(target.arch) &&
                this.os.equals(target.os) &&
                this.vendor.equals(target.vendor)) ;
    }


}
