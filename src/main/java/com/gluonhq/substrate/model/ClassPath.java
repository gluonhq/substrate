package com.gluonhq.substrate.model;

import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.ProcessRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the classpath
 * Consolidates all classpath manipulations
 */
public class ClassPath {

    private final String classPath;

    /**
     * Creates the class path
     * @param classPath standard java classpath, delimited with {@code File.pathSeparator}. Should not be null.
     */
    public ClassPath(String classPath) {
        String rawClassPath = Objects.requireNonNull(classPath);
        if (rawClassPath.startsWith("\"") && rawClassPath.endsWith("\"")) {
            this.classPath = rawClassPath.substring(1, rawClassPath.length() - 1);
        } else {
            this.classPath = rawClassPath;
        }
    }

    private Stream<String> asStream() {
        return Stream.of(classPath.split(File.pathSeparator));
    }

    /**
     * Returns whether any elements of this classpath match the provided
     * predicate.
     * @param predicate predicate to apply to elements of this classpath. Should not be null.
     * @return {@code true} if any elements of the stream match the provided
     *  predicate, otherwise {@code false}
     */
    public boolean contains(Predicate<String> predicate) {
        Objects.requireNonNull(predicate);
        return asStream().anyMatch( predicate);
    }

    /**
     * Returns a list of strings consisting of the elements of this classpath that match
     * the given predicate.
     * @param predicate predicate to apply to elements of this classpath. Should not be null.
     * @return filtered list of elements of this classpath
     */
    public List<String> filter(Predicate<String> predicate) {
        Objects.requireNonNull(predicate);
        return asStream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * Returns a List consisting of the results of applying the given
     * function to the elements of this classpath.
     *
     * @param <T> The element type of the resulting List
     * @param mapper function to apply to each element. Should not be null.
     * @return the list
     */
    public <T> List<T> mapToList( Function<String, T> mapper) {
        Objects.requireNonNull(mapper);
        return asStream().map(mapper).collect(Collectors.toList());
    }

    /**
     * Returns a String classpath consisting of the results of applying the given
     * function to the elements of this classpath.
     *
     * @param mapper function to apply to each element. Should not be null.
     * @return the string classpath
     */
    public String mapToString(Function<String, String> mapper) {
        Objects.requireNonNull(mapper);
        return asStream().map(mapper).collect(Collectors.joining(File.pathSeparator));
    }

    /**
     * Returns a String classpath consisting existing class. Tries to find libraries by name
     * and replace them with full path to this library within the given library path
     * @param libsPath library path
     * @param libNames library names to look for
     * @return the string classpath
     */
    public String mapWithLibs(Path libsPath, String... libNames) {
        Objects.requireNonNull(libsPath);
        return mapToString(s -> Arrays.stream(libNames)
                .filter(s::contains)
                .findFirst()
                .map( d -> libsPath.resolve(d + ".jar").toString())
                .orElse(s));
    }

    public List<File> getJars(boolean includeClasses) throws IOException, InterruptedException {
        List<File> jars = filter(s -> s.endsWith(".jar")).stream()
                .map(File::new)
                .collect(Collectors.toList());

        if (includeClasses) {
            // Add project's classes as a jar to the list so it can be scanned as well
            String classes = filter(s -> s.endsWith("classes") || s.endsWith("classes/java/main")).stream()
                    .findFirst()
                    .orElse(null);
            if (classes != null) {
                Path classesPath = Files.createTempDirectory("classes");
                FileOps.copyDirectory(Path.of(classes), classesPath);
                Path resourcesPath = filter(s -> s.endsWith("resources/main")).stream()
                        .findFirst()
                        .map(Path::of)
                        .orElse(null);
                if (resourcesPath != null && Files.exists(resourcesPath)) {
                    FileOps.copyDirectory(resourcesPath, classesPath);
                }
                Path jar = classesPath.resolve("classes.jar");
                ProcessRunner runner = new ProcessRunner("jar", "cf", jar.toString(), "-C", classesPath.toString(), ".");
                if (runner.runProcess("jar") == 0 && Files.exists(jar)) {
                    jars.add(jar.toFile());
                } else {
                    throw new IOException("Error creating classes.jar");
                }
            }
        }

        return jars;
    }
}
