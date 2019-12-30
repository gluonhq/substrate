package com.gluonhq.substrate.target;

public enum Vendor {
    APPLE("apple"),
    LINUX("linux"),
    MICROSOFT("microsoft");

    private final String id;

    private Vendor(String id ) {
        this.id = id.toLowerCase();
    }
    @Override
    public String toString() {
        return id;
    }

}
