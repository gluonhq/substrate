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
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IOSTest {

    @BeforeEach
    void notForTravis() {
        String[] devices = Deploy.connectedDevices();
        assumeTrue((devices != null && devices.length > 0) &&
                System.getenv("TRAVIS") == null &&
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac"));
    }

    @Test
    void iosDeployTest() {
        assertNotNull(Deploy.getIOSDeployPath());
    }

    @Test
    void deviceConnected() {
        String[] devices = Deploy.connectedDevices();
        assertNotNull(devices);
        assertTrue(devices.length > 0);
    }

    @Test
    void testSigning() {
        List<Identity> identities = CodeSigning.findIdentityByPattern();
        assertNotNull(identities);
        assertFalse(identities.isEmpty());
    }

    @Test
    void testProvisioning() {
        List<MobileProvision> provisions = CodeSigning.retrieveAllMobileProvisions();
        assertNotNull(provisions);
        assertFalse(provisions.isEmpty());
    }

    @Test
    void helloWorldTest() {
        BuildResult result = GradleRunner.create()
                .withProjectDir(new File("test-project"))
                .withGradleVersion("5.3")
                .withArguments(":helloWorld:clean", ":helloWorld:build", "-Dsubstrate.target=ios", ":helloWorld:run", ":helloWorld:runScript", "--stacktrace")
                .forwardOutput()
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":helloWorld:run").getOutcome(), "Failed build!");
        assertEquals(TaskOutcome.SUCCESS, result.task(":helloWorld:runScript").getOutcome(), "Failed build!");
    }

}
