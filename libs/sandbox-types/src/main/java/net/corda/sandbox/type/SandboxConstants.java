package net.corda.sandbox.type;

public final class SandboxConstants {
    private SandboxConstants() {
    }

    public static final String CORDA_SYSTEM = "corda.system";
    public static final String CORDA_SYSTEM_SERVICE = "corda.system:Boolean=true";
    public static final String CORDA_MARKER_ONLY_SERVICE = "corda.marker.only:Boolean=true";
    public static final String CORDA_UNINJECTABLE_SERVICE = "corda.uninjectable:Boolean=true";

    public static final String AMQP_P2P = "corda.amqp=p2p";
    public static final String AMQP_P2P_FILTER = '(' + AMQP_P2P + ')';

    public static final String AMQP_STORAGE = "corda.amqp=storage";
    public static final String AMQP_STORAGE_FILTER = '(' + AMQP_STORAGE + ')';
}
