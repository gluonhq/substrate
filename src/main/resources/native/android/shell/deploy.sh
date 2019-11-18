#!/bin/bash
set -e
export OLDJAVA=/opt/jdk1.8.0_131
export BUILDT=$ANDROID_SDK/build-tools/27.0.3
export ndk_target=android-27
export PROJ=`pwd`
export platform="aarch64-linux-android21"
export compiler="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"

rm -rf bin
rm -rf obj
mkdir bin
mkdir obj
mkdir -p lib/arm64-v8a
mkdir -p graalobj

echo "Compile Dalvik Activity with old Java compiler"
$OLDJAVA/bin/javac -d obj/ -source 1.7 -target 1.7 -cp src/dalvik -bootclasspath $SDK/platforms/$ndk_target/android.jar src/dalvik/com/gluonhq/helloandroid/*.java

echo "Package everything in an .apk"
$BUILDT/dx --dex --output=$PROJ/bin/classes.dex $PROJ/obj
$BUILDT/aapt package -f -m -F bin/hello.unaligned.apk -M AndroidManifest.xml -I $SDK/platforms/$ndk_target/android.jar
cp bin/classes.dex .
$BUILDT/aapt add bin/hello.unaligned.apk classes.dex
$BUILDT/aapt add bin/hello.unaligned.apk lib/**/*
$BUILDT/zipalign -f 4 bin/hello.unaligned.apk bin/hello.apk

echo "Sign the apk"
$BUILDT/apksigner sign --ks ~/android.keystore bin/hello.apk

echo "Install it on a device"
$BUILDT/../../platform-tools/adb install -r bin/hello.apk
