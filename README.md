# Gluon Substrate

[![Travis CI](https://travis-ci.com/gluonhq/substrate.svg?branch=master)](https://travis-ci.com/gluonhq/substrate)

Gluon Substrate is a tool that converts Java(FX) Client applications into
native executables for desktop, mobile and embedded devices.
It uses the [![GraalVM](https://graalvm.org)] GraalVM native-image tool to
compile the required Java bytecode into code that can be executed on the
target system (e.g. your desktop, on iOS, on a raspberry Pi).

Gluon Substrate deals with JavaFX resources (e.g. FXML, shader code,...)
and with platform-specific Java and native code that is part of the
JavaFX platform. 

While Gluon Substrate has an API that allows direct access to it, it
is recommended to use the [![Maven plugin](https://github.com/gluonhq/client-maven-plugin.git) which simply requires some configuration in the `.pom`
file of your project. The plugin will then invoke the Substrate API 
which in turn will use GraalVM native-image to compile the Java code,
and it will link the result with the required libraries and configuration
into a native executable.
