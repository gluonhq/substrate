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

import static com.gluonhq.substrate.TestUtils.isCIMacOS;
import static com.gluonhq.substrate.TestUtils.isLocalMacOS;
import static com.gluonhq.substrate.TestUtils.isCI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IOSTest {

    private Deploy deploy;

    private Deploy getDeploy() {
        if (deploy == null) {
            try {
                deploy = new Deploy();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        return deploy;
    }

    @BeforeEach
    void notForTravis() {
        if (!isCI()) {
            assumeTrue(isLocalMacOS());
            assumeTrue(getDeploy().getIosDeployPath() != null);
//            String[] devices = deploy.connectedDevices();
//            assumeTrue((devices != null && devices.length > 0));
        } else {
            assumeTrue(isCIMacOS());
        }
    }

    @Test
    void iosDeployTest() {
        assumeTrue(!isCI());
        assertNotNull(getDeploy().getIosDeployPath());
    }

    @Test
    void testSigning() {
        assumeTrue(!isCI());
        List<Identity> identities = CodeSigning.retrieveAllIdentities();
        assertNotNull(identities);
        assertFalse(identities.isEmpty());
    }

    @Test
    void testProvisioning() {
        assumeTrue(!isCI());
        List<MobileProvision> provisions = CodeSigning.retrieveAllMobileProvisions();
        assertNotNull(provisions);
        assertFalse(provisions.isEmpty());
    }

    @Test
    void helloWorldTest() {
        boolean skipSigning = isCI();

        BuildResult result = GradleRunner.create()
                .withProjectDir(new File("test-project"))
                .withArguments(":helloWorld:clean", ":helloWorld:build",
                        "-Djavafx.static.sdk=" + System.getenv("JAVAFX_STATIC_SDK_IOS"),
                        "-Dsubstrate.target=ios", "-Dskipsigning=" + skipSigning, "-DciEnvironment=" + (isCI() ? "true" : "false"),
                        ":helloWorld:run", ":helloWorld:runScript", "--stacktrace")
                .forwardOutput()
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":helloWorld:run").getOutcome(), "Run failed!");
        assertEquals(TaskOutcome.SUCCESS, result.task(":helloWorld:runScript").getOutcome(), "RunScript failed!");
    }

    @Test
    void helloFXTest() {
        assumeTrue(!isCI());

        BuildResult result = GradleRunner.create()
                .withProjectDir(new File("test-project"))
                .withArguments(":helloFX:clean", ":helloFX:build",
                        "-Djavafx.static.sdk=" + System.getenv("JAVAFX_STATIC_SDK_IOS"),
                        "-Dsubstrate.target=ios", "-DciEnvironment=" + (isCI() ? "true" : "false"),
                        ":helloFX:run", ":helloFX:runScript", "--stacktrace")
                .forwardOutput()
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":helloFX:run").getOutcome(), "Failed build!");
        assertEquals(TaskOutcome.SUCCESS, result.task(":helloFX:runScript").getOutcome(), "Failed build!");
    }

}
