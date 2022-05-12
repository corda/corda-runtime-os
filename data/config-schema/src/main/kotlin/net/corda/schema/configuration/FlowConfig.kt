package net.corda.schema.configuration

object FlowConfig {
        const val SESSION_MESSAGE_RESEND_WINDOW = "session.messageResendWindow"
        const val SESSION_HEARTBEAT_TIMEOUT_WINDOW = "session.heartbeatTimeout"
        const val SESSION_P2P_TTL = "session.p2pTTL"
        const val PROCESSING_MAX_RETRY_ATTEMPTS = "processing.maxRetryAttempts"
        const val PROCESSING_MAX_RETRY_DELAY = "processing.maxRetryDelay"
        const val PROCESSING_MAX_FLOW_SLEEP_DURATION = "processing.maxFlowSleepDuration"
}