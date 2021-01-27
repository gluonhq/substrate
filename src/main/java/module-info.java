module com.gluonhq.substrate {
    requires java.logging;
    requires dd.plist;
    requires java.xml;
    requires bcpkix.jdk15on;
    requires com.gluonhq.extensions;

    uses com.gluonhq.extensions.ExtensionsService;
    exports com.gluonhq.substrate.util;
}
