package com.gluonhq.substrate.target;

/**
 * Triplet architecture
 */
public enum Architecture {
    AMD64("x86_64"),
    ARM64("arm64"),
    AARCH64("aarch64");

    private final String id;

    private Architecture(String id ) {
        this.id = id.toLowerCase();
    }

    @Override
    public String toString() {
        return id;
    }
}
