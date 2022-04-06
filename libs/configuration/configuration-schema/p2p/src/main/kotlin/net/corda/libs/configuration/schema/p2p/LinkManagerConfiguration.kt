package net.corda.libs.configuration.schema.p2p

class LinkManagerConfiguration {

    companion object {
        const val PACKAGE_NAME = "net.corda.p2p.linkmanager"
        const val COMPONENT_NAME = "linkManager"
        const val CONFIG_KEY = "$PACKAGE_NAME.$COMPONENT_NAME"

        const val MAX_MESSAGE_SIZE_KEY = "maxMessageSize"
        const val REPLAY_PERIOD_KEY = "replayPeriod"
        const val BASE_REPLAY_PERIOD_KEY = "baseReplayPeriod"
        const val REPLAY_PERIOD_CUTOFF_KEY = "replayPeriodCutoff"
        const val MAX_REPLAYING_MESSAGES_PER_PEER = "maxMessages"
        const val HEARTBEAT_MESSAGE_PERIOD_KEY = "heartbeatMessagePeriod"
        const val SESSION_TIMEOUT_KEY = "sessionTimeout"
        const val SESSIONS_PER_PEER_KEY = "sessionsPerPeer"
    }

    enum class ReplayAlgorithm {
        Constant, ExponentialBackoff;
        fun configKeyName(): String {
            return this.name + this.declaringClass.simpleName
        }
    }
}