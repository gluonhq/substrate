package com.gluonhq.substrate;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.gluonhq.substrate.TestUtils.isTravis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HelloGluonTest {

    @BeforeEach
    void notForTravis() {
        assumeTrue(!isTravis());
    }

    @Test
    void helloGluonTest() {
        String expected = "QuantumRenderer: shutdown";
        BuildResult result = GradleRunner.create()
                .withProjectDir(new File("test-project"))
                .withArguments(":helloGluon:clean", ":helloGluon:build",
                        "-Dexpected=" + expected,
                        ":helloGluon:run", ":helloGluon:runScript", "--stacktrace")
                .forwardOutput()
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":helloGluon:run").getOutcome(), "Run failed!");
        assertEquals(TaskOutcome.SUCCESS, result.task(":helloGluon:runScript").getOutcome(), "RunScript failed!");
    }

}
