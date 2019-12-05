package com.gluonhq.substrate.model;

import java.io.File;
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
     * @param classPath standard java classpath, usually delimited with "pathSeparator"
     */
    public ClassPath(String classPath ) {
        this.classPath = Objects.requireNonNull(classPath);
    }

    private Stream<String> asStream() {
        return Stream.of(classPath.split(File.pathSeparator));
    }

    /**
     * Returns whether any elements of this classpath match the provided
     * predicate.
     * @param predicate predicate to apply to elements of this classpath
     * @return {@code true} if any elements of the stream match the provided
     *  predicate, otherwise {@code false}
     */
    public boolean contains( Predicate<String> predicate ) {
        Objects.requireNonNull(predicate);
        return asStream().anyMatch( predicate);
    }

    /**
     * Returns a list of strings consisting of the elements of this classpath that match
     * the given predicate.
     * @param predicate predicate to apply to elements of this classpath
     * @return filtered list of elements of this classpath
     */
    public List<String> filter( Predicate<String> predicate ) {
        Objects.requireNonNull(predicate);
        return asStream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * Returns a List consisting of the results of applying the given
     * function to the elements of this classpath.
     *
     * @param <T> The element type of the resulting List
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
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
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the string classpath
     */
    public String mapToString( Function<String, String> mapper) {
        Objects.requireNonNull(mapper);
        return asStream().map(mapper).collect(Collectors.joining(File.pathSeparator));
    }

    /**
     * Returns a String classpath consisting of the results of applying the given
     * function to the elements of this classpath. Tries to find javafx libraries and replace them with full path to this library
     * within provided JavaFX SDK
     * @param javafxSDKLibsPath JavaFX SDK library path
     * @param javafxLibs javafx library names to look for
     * @return the string classpath
     */
    public String mapWithJavaFxLibs(Path javafxSDKLibsPath, String... javafxLibs ) {
        Objects.requireNonNull(javafxSDKLibsPath);
        return mapToString(s -> Arrays.stream(javafxLibs)
                .filter(s::contains)
                .findFirst()
                .map( d -> javafxSDKLibsPath.resolve(d + ".jar").toString())
                .orElse(s));
    }

}
