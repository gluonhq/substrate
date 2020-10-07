# Gluon Substrate

[![Maven Central](https://img.shields.io/maven-central/v/com.gluonhq/substrate.svg?color=%234DC71F)](https://search.maven.org/#search|ga|1|com.gluonhq.substrate)
[![Github Actions](https://github.com/gluonhq/substrate/workflows/Substrate%20Build/badge.svg)](https://github.com/gluonhq/substrate/actions?query=workflow%3A%22Substrate+Build%22)

Gluon Substrate is a tool that converts Java(FX) Client applications into
native executables for desktop, mobile and embedded devices.
It uses the [GraalVM](https://www.graalvm.org/) GraalVM native-image tool to
compile the required Java bytecode into code that can be executed on the
target system (e.g. your desktop, on iOS, on a Raspberry Pi).

Gluon Substrate deals with JavaFX resources (e.g. FXML, shader code,...)
and with platform-specific Java and native code that is part of the
JavaFX platform. 

While Gluon Substrate has an API that allows direct access to it, it
is recommended to use the [Maven plugin](https://github.com/gluonhq/client-maven-plugin.git) which simply requires some configuration in the `pom.xml`
file of your project. The plugin will then invoke the Substrate API 
which in turn will use GraalVM native-image to compile the Java code,
and it will link the result with the required libraries and configuration
into a native executable.

There are a number of [samples](https://github.com/gluonhq/client-samples)
available that show you how to get started
with Gluon Substrate. We recommend using your favourite IDE to run those
samples.

## Compilation

### Linux hosts

To compile Gluon Substrate on Linux you need to install these development packages:

    # on ubuntu (debian flavors)
    apt-get install libasound2-dev libavcodec-dev libavformat-dev libavutil-dev libfreetype6-dev
    apt-get install libgl-dev libglib2.0-dev libgtk-3-dev libpango1.0-dev libx11-dev libxtst-dev zlib1g-dev

    # on fedora (redhat flavors), requires https://rpmfusion.org/
    dnf install alsa-lib-devel freetype-devel glib2-devel gtk3-devel libX11-devel
    dnf install libXtst-devel mesa-libGL-devel pango-devel zlib-devel
    dnf install ffmpeg-devel


## Issues and Contributions ##

 Issues can be reported to the [Issue tracker](https://github.com/gluonhq/substrate/issues)

 Contributions can be submitted via [Pull requests](https://github.com/gluonhq/substrate/pulls), 
 providing you have signed the [Gluon Individual Contributor License Agreement (CLA)](https://docs.google.com/forms/d/16aoFTmzs8lZTfiyrEm8YgMqMYaGQl0J8wA0VJE2LCCY).
