package net.corda.schema.configuration;

public final class FlowConfig {
    private FlowConfig() {
    }

    public static final String EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW = "event.messageResendWindow";
    public static final String EXTERNAL_EVENT_MAX_RETRIES = "event.maxRetries";
    public static final String SESSION_MESSAGE_RESEND_WINDOW = "session.messageResendWindow";
    public static final String SESSION_HEARTBEAT_TIMEOUT_WINDOW = "session.heartbeatTimeout";
    public static final String SESSION_P2P_TTL = "session.p2pTTL";
    public static final String SESSION_FLOW_CLEANUP_TIME = "session.cleanupTime";
    public static final String PROCESSING_MAX_RETRY_ATTEMPTS = "processing.maxRetryAttempts";
    public static final String PROCESSING_MAX_RETRY_DELAY = "processing.maxRetryDelay";
    public static final String PROCESSING_MAX_FLOW_SLEEP_DURATION = "processing.maxFlowSleepDuration";
    public static final String PROCESSING_FLOW_CLEANUP_TIME = "processing.cleanupTime";
}
