package com.gluonhq.substrate.model;

import com.gluonhq.substrate.ProjectConfiguration;
import com.gluonhq.substrate.util.Version;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.gradle.internal.impldep.org.junit.Assert.assertEquals;

class InternalProjectConfigurationTest {

    @ParameterizedTest
    @MethodSource("versioningSchemeParameters")
    void testIsOldGraalVMVersioningScheme(String version, boolean usesOldScheme) {
        Version javaVersion = new Version(version);
        InternalProjectConfiguration config = new InternalProjectConfiguration(new ProjectConfiguration("", ""));
        assertEquals(usesOldScheme, config.isOldGraalVMVersioningScheme(javaVersion));
    }

    static Stream<Arguments> versioningSchemeParameters() {
        return Stream.of(
                Arguments.of("11.0.0", true),
                Arguments.of("12.0.0", true),
                Arguments.of("13.0.0", true),
                Arguments.of("14.0.0", true),
                Arguments.of("15.0.0", true),
                Arguments.of("16.0.0", true),
                Arguments.of("17.0.0", true),
                Arguments.of("17.0.6", true),
                Arguments.of("17.0.7", false),
                Arguments.of("17.0.8", false),
                Arguments.of("17.0.9", false),
                Arguments.of("18.0.0", true),
                Arguments.of("19.0.0", true),
                Arguments.of("20.0.1", false),
                Arguments.of("21.0.0", false),
                Arguments.of("21.0.1", false),
                Arguments.of("21.0.2", false),
                Arguments.of("22.0.0", false),
                Arguments.of("22.0.1", false),
                Arguments.of("22.0.2", false)
        );
    }
}
