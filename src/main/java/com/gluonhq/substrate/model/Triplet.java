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

import com.gluonhq.substrate.target.*;

import java.util.Objects;

public class Triplet {

    private Architecture arch;
    private OS os;
    private Vendor vendor;

    /**
     * Creates a new triplet for the current runtime
     * @return the triplet for the current runtime
     * @throws IllegalArgumentException in case the current operating system is not supported
     */
    public static Triplet fromCurrentOS() throws IllegalArgumentException {
        return new Triplet( TripletProfile.fromCurrentOS());
    }

    public Triplet(String arch, String vendor, String os) {

        // following should throw exception for unsupported parts
        this.arch = Architecture.valueOf(arch);
        this.os = OS.valueOf(os);
        this.vendor = Vendor.valueOf(vendor);
    }

    public Triplet(TripletProfile profile) {
        Objects.requireNonNull(profile);
        this.arch = profile.getArch();
        this.os = profile.getOs();
    }

    /*
     * check if this host can be used to provide binaries for this target.
     * host and target should not be null.
     */
    public boolean canCompileTo(Triplet target) {
        // if the host os and target os are the same, always return true
        if (getOs().equals(target.getOs())) return true;

        // if host is linux and target is ios, fail
        return (!OS.LINUX.equals(getOs()) && !OS.WINDOWS.equals(getOs())) ||
                !OS.IOS.equals(target.getOs());
    }

    public Architecture getArch() {
        return arch;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public OS getOs() {
        return os;
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
        String myarch = this.arch.toString();
        if (  "x86_64".equals(myarch)) {
            myarch = "amd64";
        }
        return this.os+"-"+myarch;
    }

    @Override
    public String toString() {
        return arch.toString() + '-' + vendor + '-' + os;
    }
}
