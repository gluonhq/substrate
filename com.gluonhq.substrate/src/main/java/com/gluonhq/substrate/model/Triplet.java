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
package com.gluonhq.substrate.model;

import com.gluonhq.substrate.Constants;

import static com.gluonhq.substrate.Constants.*;

public class Triplet {

    private String arch;
    private String vendor;
    private String os;

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
            case MACOS:
                this.arch = ARCH_AMD64;
                this.vendor = VENDOR_APPLE;
                this.os = OS_DARWIN;
                break;
            default:
                throw new IllegalArgumentException("Triplet for profile "+profile+" is not supported yet");
        }
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

    @Override
    public String toString() {
        return arch + '-' + vendor + '-' + os;
    }
}
