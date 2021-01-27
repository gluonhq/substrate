module com.gluonhq.substrate {
    requires java.logging;
    requires dd.plist;
    requires java.xml;
    requires bcpkix.jdk15on;

    uses com.gluonhq.substrate.extensions.ExtensionsService;

    exports com.gluonhq.substrate.extensions;
    exports com.gluonhq.substrate.util;
}
