package net.corda.introspiciere.junit

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import net.corda.data.config.Configuration
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import net.corda.p2p.test.NetworkMapEntry
import net.corda.schema.Schemas
import net.corda.schema.TestSchema
import org.apache.kafka.common.config.TopicConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator

class P2pServiceConfigurationTest {

    companion object {
        @RegisterExtension
        @JvmStatic
        private val introspiciere = InMemoryIntrospiciereServer(
            // This only works locally at the moment. For CI it should read
            // this for an environment variable or from a config file
            kafkaBrokers = getMinikubeKafkaBroker()
        )

        private val suiteSuffix = "".random8
    }

    private val String.suffix: String
        get() = this + suiteSuffix

    @Test
    fun `i can configure kafka to run p2p`() {

        // TODO: To complete this test, we need to be able to publish two introspiciere servers into 2 different kafkas.

        introspiciere.client.createConfigTopic()
        introspiciere.client.createSessionOutPartitionsTopic()
        introspiciere.client.createP2pOutMarkers()
        introspiciere.client.createP2pOutMarkersStateTopic()

        val keyPairAlice = generateElipticCurveKeyPair()
        introspiciere.client.publishFakeNetworkMapData(
            "O=Alice, L=London, C=GB",
            "group-1",
            "https://alice.net:8085",
            keyPairAlice.public.encoded
        )

        val keyPairChip = generateElipticCurveKeyPair()
        introspiciere.client.publishFakeNetworkMapData(
            "O=Chip, L=London, C=GB",
            "group-1",
            "https://chip.net:8086",
            keyPairChip.public.encoded
        )

        introspiciere.client.publishIdentities("ec", keyPairAlice)
        introspiciere.client.publishIdentities("ec", keyPairChip)

        introspiciere.client.publishConfig(
            CordaConfigurationKey(
                "p2p-link-manager",
                CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME , 1, 0),
                CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 1, 0)
            ),
            config(
                LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITIES_KEY to "O=Alice,L=London,C=GB:group-1",
                LinkManagerConfiguration.MAX_MESSAGE_SIZE_KEY to 1_000_000,
                LinkManagerConfiguration.PROTOCOL_MODE_KEY to listOf(ProtocolMode.AUTHENTICATED_ENCRYPTION.toString()),
                LinkManagerConfiguration.MESSAGE_REPLAY_KEY_PREFIX + LinkManagerConfiguration.BASE_REPLAY_PERIOD_KEY_POSTFIX to 2_000L,
                LinkManagerConfiguration.MESSAGE_REPLAY_KEY_PREFIX + LinkManagerConfiguration.CUTOFF_REPLAY_KEY_POSTFIX to 10_000L,
                LinkManagerConfiguration.MESSAGE_REPLAY_KEY_PREFIX + LinkManagerConfiguration.MAX_REPLAYING_MESSAGES_PER_PEER_POSTFIX to 100,
                LinkManagerConfiguration.HEARTBEAT_MESSAGE_PERIOD_KEY to 2_000L,
                LinkManagerConfiguration.SESSION_TIMEOUT_KEY to 10_000L,
            )
        )
    }

    private fun config(vararg pairs: Pair<String, Any>): Config {
        return pairs.fold(ConfigFactory.empty()) { config, pair ->
            config.withValue(pair.first, ConfigValueFactory.fromAnyRef(pair.second))
        }
    }

    private fun IntrospiciereClient.publishConfig(key: CordaConfigurationKey, config: Config) {
        val content = Configuration(config.root().render(ConfigRenderOptions.concise()), key.componentVersion.version)
        val recordKey = "${key.packageVersion.name}.${key.componentVersion.name}"
        write(Schemas.Config.CONFIG_TOPIC, recordKey, content)
    }

    private fun IntrospiciereClient.createConfigTopic() {
        createTopic("ConfigTopic".suffix, 10, 3, mapOf(
            TopicConfig.CLEANUP_POLICY_CONFIG to "compact",
            TopicConfig.SEGMENT_MS_CONFIG to 300_000,
            TopicConfig.DELETE_RETENTION_MS_CONFIG to 300_000,
            TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG to 60_000,
            TopicConfig.MAX_COMPACTION_LAG_MS_CONFIG to 300_000,
            TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG to 0.5,
        ))
    }

    private fun IntrospiciereClient.createSessionOutPartitionsTopic() {
        createTopic("session.out.partitions".suffix, 10, 3, mapOf(
            TopicConfig.CLEANUP_POLICY_CONFIG to "compact",
            TopicConfig.SEGMENT_MS_CONFIG to 300_000,
            TopicConfig.DELETE_RETENTION_MS_CONFIG to 300_000,
            TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG to 60_000,
            TopicConfig.MAX_COMPACTION_LAG_MS_CONFIG to 300_000,
            TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG to 0.5,
        ))
    }

    private fun IntrospiciereClient.createP2pOutMarkers() {
        createTopic("p2p.out.markers".suffix, 10, 3, mapOf(
            TopicConfig.CLEANUP_POLICY_CONFIG to "delete",
        ))
    }

    private fun IntrospiciereClient.createP2pOutMarkersStateTopic() {
        createTopic("p2p.out.markers.state".suffix, 10, 3, mapOf(
            TopicConfig.CLEANUP_POLICY_CONFIG to "compact",
            TopicConfig.SEGMENT_MS_CONFIG to 300_000,
            TopicConfig.DELETE_RETENTION_MS_CONFIG to 300_000,
            TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG to 60_000,
            TopicConfig.MAX_COMPACTION_LAG_MS_CONFIG to 300_000,
            TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG to 0.5,
        ))
    }

    private fun generateElipticCurveKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(571)
        return generator.generateKeyPair()
    }

    private fun IntrospiciereClient.publishFakeNetworkMapData(
        x500Name: String,
        groupId: String,
        address: String,
        publicKey: ByteArray,
    ) {
        val networkMapEntry = NetworkMapEntry(
            HoldingIdentity(x500Name, groupId),
            ByteBuffer.wrap(publicKey),
            KeyAlgorithm.ECDSA,
            address,
            NetworkType.CORDA_5
        )
        write(TestSchema.NETWORK_MAP_TOPIC, "$x500Name-$groupId", networkMapEntry)
    }

    private fun IntrospiciereClient.publishIdentities(alias: String, pair: KeyPair) {
        val keyPairEntry = KeyPairEntry(
            KeyAlgorithm.ECDSA,
            ByteBuffer.wrap(pair.public.encoded),
            ByteBuffer.wrap(pair.private.encoded)
        )
        write(TestSchema.CRYPTO_KEYS_TOPIC, alias, keyPairEntry)
    }
}
