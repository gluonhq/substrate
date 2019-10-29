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

import com.gluonhq.substrate.util.ios.CodeSigning;
import com.gluonhq.substrate.util.ios.Deploy;
import com.gluonhq.substrate.util.ios.Identity;
import com.gluonhq.substrate.util.ios.MobileProvision;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static com.gluonhq.substrate.TestUtils.isTravis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IOSTest {

    @BeforeEach
    void notForTravis() {
        String name = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (!isTravis()) {
            assumeTrue(name.contains("mac"));
            String[] devices = Deploy.connectedDevices();
            assumeTrue((devices != null && devices.length > 0));
        } else {
            assumeTrue(name.contains("osx"));
        }
    }

    @Test
    void iosDeployTest() {
        assumeTrue(!isTravis());
        try {
            assertNotNull(Deploy.getIOSDeployPath());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void deviceConnected() {
        assumeTrue(!isTravis());
        String[] devices = Deploy.connectedDevices();
        assertNotNull(devices);
        assertTrue(devices.length > 0);
    }

    @Test
    void testSigning() {
        assumeTrue(!isTravis());
        List<Identity> identities = CodeSigning.findIdentityByPattern();
        assertNotNull(identities);
        assertFalse(identities.isEmpty());
    }

    @Test
    void testProvisioning() {
        assumeTrue(!isTravis());
        List<MobileProvision> provisions = CodeSigning.retrieveAllMobileProvisions();
        assertNotNull(provisions);
        assertFalse(provisions.isEmpty());
    }

    @Test
    void helloWorldTest() {
        boolean skipSigning = isTravis();

        BuildResult result = GradleRunner.create()
                .withProjectDir(new File("test-project"))
                .withGradleVersion("5.3")
                .withArguments(":helloWorld:clean", ":helloWorld:build",
                        "-Dsubstrate.target=ios", "-Dskipsigning=" + skipSigning,
                        ":helloWorld:run", ":helloWorld:runScript", "--stacktrace")
                .forwardOutput()
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":helloWorld:run").getOutcome(), "Failed build!");
        assertEquals(TaskOutcome.SUCCESS, result.task(":helloWorld:runScript").getOutcome(), "Failed build!");
    }

}
