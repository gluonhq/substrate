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

import java.util.Locale;

class TestUtils {

    /**
     * Checks if the test is running on Travis CI
     * @return true if on Travis CI
     */
    static boolean isTravis() {
        return System.getenv("TRAVIS") != null;
    }

    /**
     * Checks if the test is running on Github Actions CI
     * @return true if on Github Actions CI
     */
    static boolean isGithubActions() {
        return System.getenv("GITHUB_ACTIONS") != null;
    }

    /**
     * Checks if the test is running on CI server
     * @return true if on a CI server
     */
    static boolean isCI() {
        return isTravis() || isGithubActions();
    }

    /**
     * Checks if the test is running on Mac OS, but not on CI server
     * @return true if runs on a local Mac OS
     */
    static boolean isLocalMacOS() {
        return !isCI() && System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
    }

    /**
     * Checks if the test is running on Linux, but not on CI server
     * @return true if runs on a local Linux
     */
    boolean isLocalLinux() {
        return !isCI() && System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("nux");
    }

    /**
     * Checks if the test is running on Mac OS over CI server
     * @return true if runs on Mac OS over CI server
     */
    static boolean isCIMacOS() {
        return isCI() && System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
    }

    /**
     * Checks if the test is running on Linux over CI server
     * @return true if runs on Linux over CI server
     */
    static boolean isCILinux() {
        return isCI() && System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("nux");
    }
}
