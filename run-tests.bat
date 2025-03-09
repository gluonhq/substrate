@echo off

rem Removing 'out' directory
rmdir /S /Q out

set JAVAFX_PLATFORM=win
set JAVAFX_VERSION=13.0.1

set JAVAFX_STATIC_PLATFORM=win-x86_64
set JAVAFX_STATIC_VERSION=14-ea+gvm1

echo Downloading JavaFX %JAVAFX_VERSION%-%JAVAFX_PLATFORM% jars...
mkdir libs\javafx
for %%j in (base controls graphics) do (
  if not exist libs\javafx\javafx-%%j-%JAVAFX_VERSION%-%JAVAFX_PLATFORM%.jar (
    mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get -Dartifact=org.openjfx:javafx-%%j:%JAVAFX_VERSION%:jar:%JAVAFX_PLATFORM%
    mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy -Dartifact=org.openjfx:javafx-%%j:%JAVAFX_VERSION%:jar:%JAVAFX_PLATFORM% -DoutputDirectory=libs\javafx
  )
)
echo Downloading JavaFX jars completed!

echo Downloading JavaFX static SDK %JAVAFX_STATIC_VERSION%-%JAVAFX_STATIC_PLATFORM%...
mkdir libs\javafxstatic
if not exist libs\javafxstatic\openjfx-%JAVAFX_STATIC_VERSION%-%JAVAFX_STATIC_PLATFORM%-static.zip (
  powershell -Command "Invoke-WebRequest -OutFile libs\javafxstatic\openjfx-%JAVAFX_STATIC_VERSION%-%JAVAFX_STATIC_PLATFORM%-static.zip https://download2.gluonhq.com/substrate/javafxstaticsdk/openjfx-%JAVAFX_STATIC_VERSION%-%JAVAFX_STATIC_PLATFORM%-static.zip"
  tar -xf libs\javafxstatic\openjfx-%JAVAFX_STATIC_VERSION%-%JAVAFX_STATIC_PLATFORM%-static.zip -C libs\javafxstatic
)
echo Downloading JavaFX static SDK completed!

echo Downloading substrate dependencies...
mkdir libs\substrate
if not exist libs\substrate\dd-plist-1.22.jar (
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get -Dartifact=com.googlecode.plist:dd-plist:1.22
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy -Dartifact=com.googlecode.plist:dd-plist:1.22 -DoutputDirectory=libs\substrate
)
if not exist libs\substrate\jnr-ffi-2.1.11.jar (
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get -Dartifact=com.github.jnr:jnr-ffi:2.1.11
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy -Dartifact=com.github.jnr:jnr-ffi:2.1.11 -DoutputDirectory=libs\substrate
)
if not exist libs\substrate\bcpkix-jdk15on-1.49.jar (
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get -Dartifact=org.bouncycastle:bcpkix-jdk15on:1.49
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy -Dartifact=org.bouncycastle:bcpkix-jdk15on:1.49 -DoutputDirectory=libs\substrate
)
if not exist libs\substrate\bcprov-jdk15on-1.49.jar (
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get -Dartifact=org.bouncycastle:bcprov-jdk15on:1.49
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy -Dartifact=org.bouncycastle:bcprov-jdk15on:1.49 -DoutputDirectory=libs\substrate
)
echo Downloading substrate dependencies completed!

echo Compiling substrate...
set SUBSTRATE_SOURCES=
for /r %%f in (src\main\java\*.java) do (
  set SUBSTRATE_SOURCES=!SUBSTRATE_SOURCES! %%f
)

set SUBSTRATE_MODULE_PATH=
for /r %%f in (libs\substrate\*.jar) do (
  if "%SUBSTRATE_MODULE_PATH%"=="" (
    set SUBSTRATE_MODULE_PATH=%%f
  ) else (
    set SUBSTRATE_MODULE_PATH=%%f;%SUBSTRATE_MODULE_PATH%
  )
)
javac -source 11 -target 11 -d out\substrate -g -proc:none -XDuseUnsharedTable=true --module-path %SUBSTRATE_MODULE_PATH% %SUBSTRATE_SOURCES%
echo Substrate compilation done!

echo Compiling test HelloWorld...
set TEST_HELLOWORLD_SOURCES=
for /r %%f in (test-project\helloWorld\src\main\java\*.java) do (
  set TEST_HELLOWORLD_SOURCES=!TEST_HELLOWORLD_SOURCES! %%f
)
javac -source 11 -target 11 -d out\helloWorld -g -proc:none -XDuseUnsharedTable=true %TEST_HELLOWORLD_SOURCES%
echo Test HelloWorld compilation done!

echo Running test HelloWorld...
java -classpath %SUBSTRATE_MODULE_PATH%;out\substrate;src\main\resources ^
  -Dimagecp="%cd%\out\helloWorld" ^
  -Dgraalvm=%GRAALVM_HOME% ^
  -Dmainclass=com.gluonhq.substrate.test.Main ^
  com.gluonhq.substrate.SubstrateDispatcher
echo Test HelloWorld completed

echo Compiling test HelloFX...
set TEST_HELLOFX_SOURCES=
for /r %%f in (test-project\helloFX\src\main\java\*.java) do (
  set TEST_HELLOFX_SOURCES=!TEST_HELLOFX_SOURCES! %%f
)
set TEST_HELLOFX_MODULE_PATH=
for /r %%f in (libs\javafx\*.jar) do (
  set TEST_HELLOFX_MODULE_PATH=!cd!\%%f;%TEST_HELLOFX_MODULE_PATH%
)
javac -source 11 -target 11 -d out\helloFX -g -proc:none -XDuseUnsharedTable=true --module-path %TEST_HELLOFX_MODULE_PATH% --add-modules javafx.controls %TEST_HELLOFX_SOURCES%
echo Test HelloFX compilation done!

echo Running test HelloFX...
java -classpath %SUBSTRATE_MODULE_PATH%;out\substrate;src\main\resources ^
  -Dimagecp="%cd%\out\helloFX;%TEST_HELLOFX_MODULE_PATH%" ^
  -Dgraalvm=%GRAALVM_HOME% ^
  -Dmainclass=com.gluonhq.substrate.test.Main ^
  -Djavafxsdk=libs\javafxstatic\sdk ^
  -Dprism.sw=true ^
  com.gluonhq.substrate.SubstrateDispatcher
echo Test HelloFX completed
