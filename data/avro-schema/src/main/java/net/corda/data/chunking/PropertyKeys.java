package net.corda.data.chunking;

public final class PropertyKeys {
    private PropertyKeys() {
    }

    /**
     * Boolean property specifying whether same data content can re-upload again
     */
    public static final String FORCE_UPLOAD = "forceUpload";

    /**
     * Boolean property specifying whether the vnode databases should be deleted and recreated
     */
    public static final String RESET_DB = "resetDb";

    /**
     * String property specifying the RPC actor that made the request
     */
    public static final String ACTOR = "actor";
}
