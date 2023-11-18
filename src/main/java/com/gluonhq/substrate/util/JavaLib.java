/*
 * Copyright (c) 2023, Gluon
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
package com.gluonhq.substrate.util;

/**
 * This class represents a java library constrained to the optional lower and upper bounds.
 */
public class JavaLib {
    /**
     * lower bound of the applicable version range, inclusive
     */
    private final Integer lowerBound;
    /**
     * upper bound of the applicable version range, inclusive
     */
    private final Integer upperBound;
    private final String libName;

    private JavaLib(Integer lowerBound, Integer upperBound, String libName) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.libName = libName;
    }

    public String getLibName() {
        return libName;
    }

    public boolean inRange(int javaMajorVersion) {
        // Java version is lower than the lower bound
        if (lowerBound != null && javaMajorVersion < lowerBound) return false;
        // Java version is higher than the upper bound
        if (upperBound != null && upperBound < javaMajorVersion) return false;
        return true;
    }

    public static JavaLib of(String libName) {
        return new JavaLib(null, null, libName);
    }

    public static JavaLib range(int lowerBound, int upperBound, String libName) {
        return new JavaLib(lowerBound, upperBound, libName);
    }

    public static JavaLib from(int lowerBound, String libName) {
        return new JavaLib(lowerBound, null, libName);
    }

    public static JavaLib upto(int upperBound, String libName) {
        return new JavaLib(null, upperBound, libName);
    }
}
