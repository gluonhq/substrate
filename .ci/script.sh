#!/usr/bin/env bash
echo "hello, ci script for $1"
echo "which java: $(which java)"
sh ./gradlew build

if [ "$1" = "linux" ]
then
  gcc --version
  wget https://download2.gluonhq.com/substrate/graalvm/graalvm-unknown-java11-19.3.0-dev-gvm-1-linux-x86_64.zip
  unzip graalvm-unknown-java11-19.3.0-dev-gvm-1-linux-x86_64.zip
  export graalvmPath="graalvm-unknown-java11-19.3.0-dev"
elif [ "$1" = "osx" ]
then
  wget https://download2.gluonhq.com/substrate/graalvm/graalvm-unknown-java11-19.3.0-dev-gvm-1-darwin-amd64.zip
  unzip graalvm-unknown-java11-19.3.0-dev-gvm-1-darwin-amd64.zip
  export graalvmPath="graalvm-unknown-java11-19.3.0-dev/Contents/Home"
else
  echo "OS $1 not supported"
  exit 1
fi
echo "public class HelloWorld { public static void main(String[] args) { System.out.println(\"Hello World\"); } }" > HelloWorld.java
javac HelloWorld.java
echo "Run SubstrateDispatcher with $graalvmPath:"
java -cp com.gluonhq.substrate/build/libs/com.gluonhq.substrate-0.0.1-SNAPSHOT.jar -Dimagecp=. -Dgraalvm="$graalvmPath" -Dmainclass=HelloWorld com.gluonhq.substrate.SubstrateDispatcher
