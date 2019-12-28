package com.gluonhq.substrate.target;

import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;

import java.util.Locale;
import java.util.function.BiFunction;

public enum OS {

    DARWIN("darwin", "DARWIN_AMD64", Vendor.APPLE, DarwinTargetConfiguration::new),
    IOS("ios", "DARWIN_AARCH64", Vendor.APPLE, IosTargetConfiguration::new),
    LINUX("linux", "LINUX_AMD64", Vendor.LINUX, LinuxTargetConfiguration::new),
    WINDOWS("windows", "WINDOWS_AMD64", Vendor.MICROSOFT, WindowsTargetConfiguration::new),
    ANDROID("android", "LINUX_AARCH64", Vendor.LINUX, AndroidTargetConfiguration::new);

    private final String id;
    private final String jniPlatform;
    private final Vendor vendor;
    private final BiFunction<ProcessPaths, InternalProjectConfiguration, TargetConfiguration> targetConfiguration;

    public static OS current() {
        String osName  = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (osName.contains("mac")) {
            return DARWIN;
        } else if (osName.contains("nux")) {
            return LINUX;
        } else if (osName.contains("windows")) {
            return WINDOWS;
        } else {
            throw new IllegalArgumentException("OS " + osName + " not supported");
        }
    }

    private OS(String id,
               String jniPlatform,
               Vendor vendor,
               BiFunction<ProcessPaths, InternalProjectConfiguration, TargetConfiguration> targetConfiguration) {
        this.id = id.toLowerCase();
        this.jniPlatform = jniPlatform;
        this.vendor = vendor;
        this.targetConfiguration = targetConfiguration;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public String getJniPlatform() {
        return jniPlatform;
    }

    public TargetConfiguration getTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration ) {
        return targetConfiguration.apply(paths, configuration);
    }

    @Override
    public String toString() {
        return id;
    }
}
