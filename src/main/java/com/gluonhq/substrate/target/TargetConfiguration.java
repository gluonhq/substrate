/*
 * Copyright (c) 2019, 2022, Gluon
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

public interface TargetConfiguration {

    /**
     * Compiles the classes to objectcode for this TargetConfiguration.
     *
     * @return true if compilation succeeded, false if it failed.
     * @throws Exception
     */
    boolean compile() throws Exception;

    /**
    * Links a previously created objectfile with the required
    * dependencies into a native executable or library
    * @return true if linking succeeded, false otherwise
    */
    boolean link() throws IOException, InterruptedException;

    /**
     * Creates a package of the application (including at least executable and
     * other possible files) in a given format.
     *
     * This operation has to be called only after link has successfully produced
     * a valid application
     *
     * @return true if packaging succeeded or is a no-op, false if it failed.
     * @throws IOException
     * @throws InterruptedException
     */
    boolean packageApp() throws IOException, InterruptedException;

    /**
     * Installs the packaged application on the local system or on a device
     * that is attached to the local system.
     *
     * This operation has to be called only after {@link #packageApp()} has successfully produced
     * a valid package.
     *
     * @return true if installing succeeded or is a no-op, false if it failed.
     * @throws IOException
     * @throws InterruptedException
     */
    boolean install() throws IOException, InterruptedException;

    /**
     * Runs the application, and if successful, returns the last line
     * printed out by the process
     * @return A string (it can be empty) or null if the process failed
     * @throws IOException
     * @throws InterruptedException
     */
    String run() throws IOException, InterruptedException;

    /**
     * Runs the application, and if successful, returns true
     * @return true if the process succeeded or false if the process failed
     * @throws IOException
     * @throws InterruptedException
     */
    boolean runUntilEnd() throws IOException, InterruptedException;

    /**
     * Creates a native image that can be used as shared library
     * @return true if the process succeeded or false if the process failed
     * @throws IOException
     * @throws InterruptedException
     */
    boolean createSharedLib() throws IOException, InterruptedException;

    /**
     * Creates a static library
     * @return true if the process succeeded or false if the process failed
     * @throws IOException
     * @throws InterruptedException
     */
    boolean createStaticLib() throws IOException, InterruptedException;

}
