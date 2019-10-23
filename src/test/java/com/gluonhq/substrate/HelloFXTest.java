package com.gluonhq.substrate;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HelloFXTest {

    @BeforeEach
    void notForTravis() {
        assumeTrue(System.getenv("TRAVIS") == null);
    }

    @Test
    void helloFXTest() {
        BuildResult result = GradleRunner.create()
                .withProjectDir(new File("test-project"))
                .withGradleVersion("5.3")
                .withArguments(":helloFX:clean", ":helloFX:build", ":helloFX:run", ":helloFX:runScript", "--stacktrace")
                .forwardOutput()
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":helloFX:run").getOutcome(), "Failed build!");
        assertEquals(TaskOutcome.SUCCESS, result.task(":helloFX:runScript").getOutcome(), "Failed build!");
    }

}
