package com.gluonhq.substrate;

public class Main {

    public static void main(String[] args) {
        System.err.println("Main!");
        NativeImage ni = new NativeImage();
        String gr = "/home/johan/graal/github/fork/graal/vm/mxbuild/linux-amd64/GRAALVM_UNKNOWN/graalvm-unknown-19.3.0-dev";
        int ret = ni.compile(gr, "/tmp", "HelloWorld");
        System.err.println("Return value of compile = "+ret);
    }

}
