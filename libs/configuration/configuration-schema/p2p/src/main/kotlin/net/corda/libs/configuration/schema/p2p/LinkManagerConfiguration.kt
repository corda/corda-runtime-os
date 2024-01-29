package net.corda.libs.configuration.schema.p2p

class LinkManagerConfiguration {

    companion object {
        const val MAX_MESSAGE_SIZE_KEY = "maxMessageSize"
        const val MESSAGE_REPLAY_PERIOD_KEY = "messageReplayPeriod"
        const val BASE_REPLAY_PERIOD_KEY = "baseReplayPeriod"
        const val REPLAY_PERIOD_CUTOFF_KEY = "replayPeriodCutoff"
        const val MAX_REPLAYING_MESSAGES_PER_PEER = "maxReplayingMessages"
        const val HEARTBEAT_ENABLED_KEY = "heartbeatEnabled"
        const val HEARTBEAT_MESSAGE_PERIOD_KEY = "heartbeatMessagePeriod"
        const val SESSION_TIMEOUT_KEY = "sessionTimeout"
        const val SESSIONS_PER_PEER_KEY = "sessionsPerPeer"
        const val SESSIONS_PER_PEER_FOR_MEMBER_KEY = "numOfSessionsPerPeer.forMembers"
        const val SESSIONS_PER_PEER_FOR_MGM_KEY = "numOfSessionsPerPeer.forMgm"
        const val REPLAY_ALGORITHM_KEY = "replayAlgorithm"
        const val REVOCATION_CHECK_KEY = "revocationCheck.mode"
        const val SESSION_REFRESH_THRESHOLD_KEY = "sessionRefreshThreshold"
    }

    enum class ReplayAlgorithm {
        Constant, ExponentialBackoff;
        fun configKeyName(): String {
            return this.name.replaceFirstChar { it.lowercase() }
        }
    }
}