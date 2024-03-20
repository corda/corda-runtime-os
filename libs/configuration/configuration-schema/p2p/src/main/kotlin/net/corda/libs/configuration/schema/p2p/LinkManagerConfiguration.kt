package net.corda.libs.configuration.schema.p2p

class LinkManagerConfiguration {

    companion object {
        const val MAX_MESSAGE_SIZE_KEY = "maxMessageSize"
        const val MESSAGE_REPLAY_PERIOD_KEY = "messageReplayPeriod"
        const val BASE_REPLAY_PERIOD_KEY = "baseReplayPeriod"
        const val REPLAY_PERIOD_CUTOFF_KEY = "replayPeriodCutoff"
        const val MAX_REPLAYING_MESSAGES_PER_PEER = "maxReplayingMessages"
        const val SESSION_TIMEOUT_KEY = "sessionTimeout"
        const val REPLAY_ALGORITHM_KEY = "replayAlgorithm"
        const val REVOCATION_CHECK_KEY = "revocationCheck.mode"
        const val INBOUND_SESSIONS_CACHE_SIZE = "sessionCache.inboundSessionsCacheSize"
        const val OUTBOUND_SESSIONS_CACHE_SIZE = "sessionCache.outboundSessionsCacheSize"
        const val DELIVERY_TRACKER_MAX_CACHE_SIZE_MEGABYTES = "deliveryTracker.maxCacheSizeMegabytes"
        const val DELIVERY_TRACKER_MAX_CACHE_OFFSET_AGE = "deliveryTracker.maxCacheOffsetAge"
        const val DELIVERY_TRACKER_STATE_PERSISTENCE_PERIOD_SECONDS = "deliveryTracker.statePersistencePeriodSeconds"
        const val DELIVERY_TRACKER_OUTBOUND_BATCH_PROCESSING_TIMEOUT_SECONDS = "deliveryTracker.outboundBatchProcessingTimeoutSeconds"
        const val DELIVERY_TRACKER_MAX_NUMBER_OF_PERSISTENCE_RETRIES = "deliveryTracker.maxNumberOfPersistenceRetries"
    }

    enum class ReplayAlgorithm {
        Constant, ExponentialBackoff;
        fun configKeyName(): String {
            return this.name.replaceFirstChar { it.lowercase() }
        }
    }
}