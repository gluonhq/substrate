package com.gluonhq.substrate;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelloWorldTest {

    @Test
    void helloWorldTest() {
        BuildResult result = GradleRunner.create()
                .withProjectDir(new File("test-project"))
                .withGradleVersion("5.3")
                .withArguments(":helloWorld:clean", ":helloWorld:build", ":helloWorld:run", ":helloWorld:runScript", "--stacktrace")
                .forwardOutput()
                .build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":helloWorld:run").getOutcome(), "Failed build!");
        assertEquals(TaskOutcome.SUCCESS, result.task(":helloWorld:runScript").getOutcome(), "Failed build!");
    }

}
