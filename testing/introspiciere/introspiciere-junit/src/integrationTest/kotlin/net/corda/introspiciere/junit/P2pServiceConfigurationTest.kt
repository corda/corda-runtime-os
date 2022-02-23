package net.corda.introspiciere.junit

import org.apache.kafka.common.config.TopicConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class P2pServiceConfigurationTest {

    companion object {
        @RegisterExtension
        @JvmStatic
        private val introspiciere = InMemoryIntrospiciereServer(
            // This only works locally at the moment. For CI it should read
            // this for an environment variable or from a config file
            kafkaBrokers = getMinikubeKafkaBroker()
        )
    }

    @Test
    fun `i can configure kafka to run p2p`() {
        introspiciere.client.createTopic("ConfigTopic".random8, 10, 3, mapOf(
            TopicConfig.CLEANUP_POLICY_CONFIG to "compact",
            TopicConfig.SEGMENT_MS_CONFIG to 300_000,
            TopicConfig.DELETE_RETENTION_MS_CONFIG to 300_000,
            TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG to 60_000,
            TopicConfig.MAX_COMPACTION_LAG_MS_CONFIG to 300_000,
            TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG to 0.5,
        ))
    }

}