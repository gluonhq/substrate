package com.gluonhq.substrate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class NativeImage {

    private boolean useJNI = true;

    public void setUseJNI(boolean v) {
        this.useJNI = v;
    }

    public int compile(String graalVMRoot, String classPath, String mainClass) {
        ProcessBuilder pb = new ProcessBuilder();
        List<String> command = pb.command();
        command.add(getNativeImageExecutable(graalVMRoot).toString());
        command.add("-cp");
        command.add(classPath);
        addJNIParameters (command);

        command.add(mainClass);
        int exitStatus = 1;
        try {
            pb.redirectError(new File("/tmp/error"));
            pb.redirectOutput(new File("/tmp/output"));
            Process p = pb.start();
            exitStatus = p.waitFor();
            System.err.println("ExitStatus = "+exitStatus);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            exitStatus = -1;
        }
        return exitStatus;
    }

    // add all parameters required to deal with JNI platform or not
    private void addJNIParameters (List<String> command) {
        if (useJNI) {
            command.add("-Dsvm.platform=org.graalvm.nativeimage.impl.InternalPlatform$LINUX_JNI_AMD64");
            command.add("-H:+ExitAfterRelocatableImageWrite");
        }
    }

    Path getNativeImageExecutable (String graalVMRoot) {
        Path nativeImage = Path.of(graalVMRoot).resolve("bin").resolve("native-image");
        return nativeImage;
    }

}
