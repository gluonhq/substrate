package com.gluonhq.substrate.target;

import com.gluonhq.substrate.Constants;

/**
 * Predefined Profiles
 */
public enum TripletProfile {
    LINUX(Architecture.AMD64, OS.LINUX), // (x86_64-linux-linux)
    MACOS(Architecture.AMD64, OS.DARWIN), // (x86_64-apple-darwin)
    WINDOWS(Architecture.AMD64, OS.WINDOWS), // (x86_64-windows-windows)
    IOS(Architecture.ARM64, OS.IOS),   // (aarch64-apple-ios)
    IOS_SIM(Architecture.AMD64, OS.IOS),   // (x86_64-apple-ios)
    ANDROID(Architecture.AARCH64,OS.ANDROID); // (aarch64-linux-android)

    private final Architecture arch;
    private final OS os;

    public static TripletProfile fromCurrentOS() {
        switch (OS.current()) {
            case DARWIN: return TripletProfile.MACOS;
            case IOS: return TripletProfile.IOS;
            case LINUX: return TripletProfile.LINUX;
            case WINDOWS: return TripletProfile.WINDOWS;
            case ANDROID: return TripletProfile.ANDROID;
            default: throw new IllegalArgumentException("Unsupported OS");
        }
    }

    TripletProfile(Architecture arch, OS os ) {
        this.arch = arch;
        this.os = os;
    }

    public Architecture getArch() {
        return arch;
    }

    public OS getOs() {
        return os;
    }
}
