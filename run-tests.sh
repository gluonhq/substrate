rm -rf out

JAVAFX_PLATFORM=linux
JAVAFX_VERSION=13.0.1

JAVAFX_STATIC_PLATFORM=linux-x86_64
JAVAFX_STATIC_VERSION=14-ea+gvm1

echo "Downloading JavaFX ${JAVAFX_VERSION}-${JAVAFX_PLATFORM} jars..."
mkdir -p libs/javafx
for javafxModule in `echo "base controls graphics"`; do
  if [ ! -f libs/javafx/javafx-${javafxModule}-${JAVAFX_VERSION}-${JAVAFX_PLATFORM}.jar ]; then
    mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get -Dartifact=org.openjfx:javafx-${javafxModule}:${JAVAFX_VERSION}:jar:${JAVAFX_PLATFORM}
    mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy -Dartifact=org.openjfx:javafx-${javafxModule}:${JAVAFX_VERSION}:jar:${JAVAFX_PLATFORM} -DoutputDirectory=libs/javafx
  fi
done
echo "Downloading JavaFX jars completed!"

echo "Downloading JavaFX static SDK ${JAVAFX_STATIC_VERSION}-${JAVAFX_STATIC_PLATFORM}..."
mkdir -p libs/javafxstatic
if [ ! -f libs/javafxstatic/openjfx-${JAVAFX_STATIC_VERSION}-${JAVAFX_STATIC_PLATFORM}-static.zip ]; then
  wget -O libs/javafxstatic/openjfx-${JAVAFX_STATIC_VERSION}-${JAVAFX_STATIC_PLATFORM}-static.zip https://download2.gluonhq.com/substrate/javafxstaticsdk/openjfx-${JAVAFX_STATIC_VERSION}-${JAVAFX_STATIC_PLATFORM}-static.zip
  unzip -d libs/javafxstatic libs/javafxstatic/openjfx-${JAVAFX_STATIC_VERSION}-${JAVAFX_STATIC_PLATFORM}-static.zip
fi
echo "Downloading JavaFX static SDK completed!"

echo "Downloading substrate dependencies..."
mkdir -p libs/substrate
if [ ! -f libs/substrate/dd-plist-1.22.jar ]; then
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get -Dartifact=com.googlecode.plist:dd-plist:1.22
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy -Dartifact=com.googlecode.plist:dd-plist:1.22 -DoutputDirectory=libs/substrate
fi
if [ ! -f libs/substrate/jnr-ffi-2.1.11.jar ]; then
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get -Dartifact=com.github.jnr:jnr-ffi:2.1.11
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy -Dartifact=com.github.jnr:jnr-ffi:2.1.11 -DoutputDirectory=libs/substrate
fi
if [ ! -f libs/substrate/bcpkix-jdk15on-1.49.jar ]; then
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get -Dartifact=org.bouncycastle:bcpkix-jdk15on:1.49
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy -Dartifact=org.bouncycastle:bcpkix-jdk15on:1.49 -DoutputDirectory=libs/substrate
fi
if [ ! -f libs/substrate/bcprov-jdk15on-1.49.jar ]; then
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:get -Dartifact=org.bouncycastle:bcprov-jdk15on:1.49
  mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy -Dartifact=org.bouncycastle:bcprov-jdk15on:1.49 -DoutputDirectory=libs/substrate
fi
echo "Downloading substrate dependencies completed!"

echo "Compiling substrate..."
SUBSTRATE_SOURCES=""
for javaSource in `find src/main/java -name *.java`; do
  SUBSTRATE_SOURCES="${SUBSTRATE_SOURCES} ${javaSource}"
done
SUBSTRATE_MODULE_PATH=""
for moduleJar in `ls -1 libs/substrate/*.jar`; do
  if [ "${SUBSTRATE_MODULE_PATH}" == "" ]; then
    SUBSTRATE_MODULE_PATH="${moduleJar}"
  else
    SUBSTRATE_MODULE_PATH="${moduleJar}:${SUBSTRATE_MODULE_PATH}"
  fi
done
javac -source 11 -target 11 -d out/substrate -g -proc:none -XDuseUnsharedTable=true --module-path ${SUBSTRATE_MODULE_PATH} ${SUBSTRATE_SOURCES}
echo "substrate compilation done!"



echo "Compiling test HelloWorld..."
TEST_HELLOWORLD_SOURCES=""
for javaSource in `find test-project/helloWorld/src/main/java -name *.java`; do
  TEST_HELLOWORLD_SOURCES="${TEST_HELLOWORLD_SOURCES} ${javaSource}"
done
javac -source 11 -target 11 -d out/helloWorld -g -proc:none -XDuseUnsharedTable=true ${TEST_HELLOWORLD_SOURCES}
echo "test HelloWorld compilation done!"

echo "Running test HelloWorld..."
java -classpath ${SUBSTRATE_MODULE_PATH}:out/substrate:src/main/resources \
 -Dimagecp="$(pwd)/out/helloWorld" \
 -Dgraalvm=${GRAALVM_HOME} \
 -Dmainclass=com.gluonhq.substrate.test.Main \
 com.gluonhq.substrate.SubstrateDispatcher
echo "test HelloWorld completed"



echo "Compiling test HelloFX..."
TEST_HELLOFX_SOURCES=""
for javaSource in `find test-project/helloFX/src/main/java -name *.java`; do
  TEST_HELLOFX_SOURCES="${TEST_HELLOFX_SOURCES} ${javaSource}"
done
TEST_HELLOFX_MODULE_PATH=""
for moduleJar in `find libs/javafx -name *.jar`; do
  TEST_HELLOFX_MODULE_PATH="$(pwd)/${moduleJar}:${TEST_HELLOFX_MODULE_PATH}"
done
javac -source 11 -target 11 -d out/helloFX -g -proc:none -XDuseUnsharedTable=true --module-path ${TEST_HELLOFX_MODULE_PATH} --add-modules javafx.controls ${TEST_HELLOFX_SOURCES}
echo "test HelloFX compilation done!"

echo "Running test HelloFX"
java -classpath ${SUBSTRATE_MODULE_PATH}:out/substrate:src/main/resources \
 -Dimagecp="$(pwd)/out/helloFX:${TEST_HELLOFX_MODULE_PATH}" \
 -Dgraalvm=${GRAALVM_HOME} \
 -Dmainclass=com.gluonhq.substrate.test.Main \
 -Djavafxsdk=libs/javafxstatic/sdk \
 -Dprism.sw=true \
 com.gluonhq.substrate.SubstrateDispatcher
echo "test HelloFX completed"
