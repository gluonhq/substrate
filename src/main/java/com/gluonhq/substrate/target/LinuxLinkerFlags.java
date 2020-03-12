/*
 * Copyright (c) 2020, Gluon
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.gluonhq.substrate.target.LinuxFlavor.Flavor;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;

import static com.gluonhq.substrate.target.LinuxLinkerFlags.PkgInfo.activeOf;
import static com.gluonhq.substrate.target.LinuxLinkerFlags.PkgInfo.debian;
import static com.gluonhq.substrate.target.LinuxLinkerFlags.PkgInfo.fedora;
import static com.gluonhq.substrate.target.LinuxLinkerFlags.PkgInfo.hardwired;

/**
 * Defines linker flags for a given linux flavor (debian/fedora).
 */
public class LinuxLinkerFlags {
    private static final Flavor flavor = LinuxFlavor.getFlavor();

    /**
     * Defines per-flavor pkg-config package names and the associated OS package
     * name that provides it.
     */
    private static final List<PkgInfo> LINK_DEPENDENCIES = List.of(
        hardwired("-Wl,--no-whole-archive"),

        activeOf(debian("gl", "libgl-dev"),
                 fedora("gl", "mesa-libGL-devel")),
        activeOf(debian("x11", "libx11-dev"),
                 fedora("x11", "libX11-devel")),
        activeOf(debian("gtk+-x11-3.0", "libgtk-3-dev"),
                 fedora("gtk+-3.0", "gtk3-devel")),
        activeOf(debian("freetype2", "libfreetype6-dev"),
                 fedora("freetype2", "freetype-devel")),
        activeOf(debian("pangoft2", "libpango1.0-dev"),
                 fedora("pangoft2", "pango-devel")),

        hardwired("-lgstreamer-lite"),

        activeOf(debian("gthread-2.0", "libglib2.0-dev"),
                 fedora("gthread-2.0", "glib2-devel")),

        hardwired("-lstdc++"),

        activeOf(debian("zlib", "zlib1g-dev"),
                 fedora("zlib", "zlib-devel")),

        activeOf(debian("xtst", "libxtst-dev"),
                 fedora("xtst", "libXtst-devel")),

        // On fedora these require https://rpmfusion.org/
        activeOf(debian("libavcodec", "libavcodec-dev"),
                 fedora("libavcodec", "ffmpeg-devel")),
        activeOf(debian("libavformat", "libavformat-dev"),
                 fedora("libavformat", "ffmpeg-devel")),
        activeOf(debian("libavutil", "libavutil-dev"),
                 fedora("libavutil", "ffmpeg-devel")),

        activeOf(debian("alsa", "libasound2-dev"),
                 fedora("alsa", "alsa-lib-devel")),

        hardwired("-lm"),

        activeOf(debian("gmodule-no-export-2.0", "libglib2.0-dev"),
                 fedora("gmodule-no-export-2.0", "glib2-devel"))
    );

    /**
     * List of packages found missing on computer.
     */
    private List<String> missingPackages = new ArrayList<>();
    
    private LinuxLinkerFlags() {}

    /**
     * Returns linker flag appropriate for the current linux variant.
     *
     * Consults the system pkg-config for all required packages
     * and combines their associated linker flags.
     *
     * If any of the packages are missing, this is cause for abort
     * with information to the user about which OS packages need
     * to be installed.
     *
     * @return linker flag appropriate for the current linux variant.
     * @throws InterruptedException 
     * @throws IOException 
     */
    public static List<String> getLinkerFlags() throws IOException, InterruptedException {
        return new LinuxLinkerFlags().doGetLinkerFlags();
    }
    
    private List<String> doGetLinkerFlags() throws IOException, InterruptedException {
        List<String> pkgFlags = new ArrayList<>();
        for (PkgInfo pkg : LINK_DEPENDENCIES) {
            pkgFlags.addAll(lookupPackageFlags(pkg));
        }

        if (anyOsPackageIsMissing()) {
            printUpdateInstructionsAndFail();
        }

        Logger.logDebug("All flags: " + pkgFlags);
        return pkgFlags;
    }

    /**
     * Uses pkg-config to lookup linker flags for package.
     *
     * If pkg-config fails, adds amendment instructions to missingPackages.
     */
    private List<String> lookupPackageFlags(PkgInfo pkgInfo) throws IOException, InterruptedException {
        if (pkgInfo.hardwired != null) {
            return List.of(pkgInfo.hardwired);
        }

        String pkgName = pkgInfo.pkgName;
        ProcessRunner process = new ProcessRunner("/usr/bin/pkg-config", "--libs", pkgName);
        if (process.runProcess("Get config for " + pkgName) != 0) {
            missingPackages.add(pkgInfo.installName + " (for pkgConfig " + pkgName + ")");
            return List.of();
        }

        List<String> flags = List.of(process.getResponse().trim().split(" "));
        Logger.logDebug("Pkg " + pkgName + " provided flags: " + flags);
        return flags;
    }

    private boolean anyOsPackageIsMissing() {
        return !missingPackages.isEmpty();
    }    
    
    private void printUpdateInstructionsAndFail() {
        String nl = System.lineSeparator();
        String nlIndent = nl + "  ";
        String instructions = missingPackages.stream()
                .sorted()
                .distinct()
                .collect(Collectors.joining(nlIndent));

        Logger.logInfo("Cannot link because some development libraries are missing."
                + nl + "Please install OS packages:"
                + nlIndent + instructions);
        throw new IllegalStateException("Missing linker libraries");
    }

    /**
     * Information about a compilation package.
     * 
     * @param pkgName     name as used by pkg-config
     * @param installName name as used by OS package manager
     */
    static class PkgInfo {
        public final String pkgName;
        public final String installName;
        public final String hardwired;

        private PkgInfo(String pkgName, String installName, String hardwired) {
            this.pkgName = pkgName;
            this.installName = installName;
            this.hardwired = hardwired;
        }

        public static PkgInfo debian(String pkgName, String installName) {
            return new PkgInfo(pkgName, installName, null);
        }

        public static PkgInfo fedora(String pkgName, String installName) {
            return new PkgInfo(pkgName, installName, null);
        }

        public static PkgInfo activeOf(PkgInfo debian, PkgInfo fedora) {
            return flavor.isDebNaming() ? debian : fedora;
        }

        public static PkgInfo hardwired(String flag) {
            return new PkgInfo("", "", flag);
        }
    }
}
