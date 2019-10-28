package com.gluonhq.substrate;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HelloGluonTest {

    @BeforeEach
    void notForTravis() {
        assumeTrue(System.getenv("TRAVIS") == null);
    }

    @Test
    void helloGluonTest() {
        BuildResult result = GradleRunner.create()
                .withProjectDir(new File("test-project"))
                .withGradleVersion("5.3")
                .withArguments(":helloGluon:clean", ":helloGluon:build", ":helloGluon:run", ":helloGluon:runScript", "--stacktrace")
                .forwardOutput()
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":helloGluon:run").getOutcome(), "Failed build!");
        assertEquals(TaskOutcome.SUCCESS, result.task(":helloGluon:runScript").getOutcome(), "Failed build!");
    }

}
