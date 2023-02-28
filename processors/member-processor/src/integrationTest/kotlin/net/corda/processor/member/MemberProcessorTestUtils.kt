package net.corda.processor.member

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.config.impl.createCryptoBootstrapParamsMap
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.core.CryptoConsts
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.Lifecycle
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.registration.RegistrationProxy
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MembershipConfig.MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES
import net.corda.test.util.eventually
import net.corda.test.util.time.TestClock
import net.corda.utilities.millis
import net.corda.utilities.seconds
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.calculateHash
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MemberProcessorTestUtils {
    companion object {

        private val clock = TestClock(Instant.ofEpochSecond(100))

        private const val MESSAGING_CONFIGURATION_VALUE: String = """
            componentVersion="5.1"
            subscription {
                consumer {
                    close.timeout = 6000
                    poll.timeout = 6000
                    thread.stop.timeout = 6000
                    processor.retries = 3
                    subscribe.retries = 3
                    commit.retries = 3
                }
                producer {
                    close.timeout = 6000
                }
            }
      """

        private const val BOOT_CONFIGURATION = """
        instanceId=1
        bus.busType = INMEMORY
    """

        private const val KEY_SCHEME = "corda.key.scheme"

        fun makeMembershipConfig() : SmartConfig =
            SmartConfigFactory.createWithoutSecurityServices().create(
                ConfigFactory.empty()
                    .withValue(MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES,
                        ConfigValueFactory.fromAnyRef(100L))
            )

        private val smartConfigFactory: SmartConfigFactory = SmartConfigFactory.createWith(
            ConfigFactory.parseString(
                """
            ${EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY}=passphrase
            ${EncryptionSecretsServiceFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            ),
            listOf(EncryptionSecretsServiceFactory())
        )

        fun makeCryptoConfig(): SmartConfig = createDefaultCryptoConfig("master-key-pass", "master-key-salt")

        fun makeMessagingConfig(): SmartConfig =
            smartConfigFactory.create(
                ConfigFactory.parseString(MESSAGING_CONFIGURATION_VALUE)
                    .withFallback(ConfigFactory.parseString(BOOT_CONFIGURATION))
            )

        fun makeBootstrapConfig(extra: Map<String, SmartConfig>): SmartConfig {
            var cfg = smartConfigFactory.create(
                ConfigFactory
                    .parseString(MESSAGING_CONFIGURATION_VALUE)
                    .withFallback(
                        ConfigFactory.parseString(BOOT_CONFIGURATION)
                    )
                    .withFallback(
                        ConfigFactory.parseMap(
                            mapOf(
                                BOOT_CRYPTO to createCryptoBootstrapParamsMap(CryptoConsts.SOFT_HSM_ID)
                            )
                        )
                    )
            )
            extra.forEach {
                cfg = cfg.withFallback(cfg.withValue(it.key, ConfigValueFactory.fromMap(it.value.root().unwrapped())))
            }
            return cfg
        }

        const val aliceName = "C=GB, L=London, O=Alice"
        const val bobName = "C=GB, L=London, O=Bob"
        const val charlieName = "C=GB, L=London, O=Charlie"

        val aliceX500Name = MemberX500Name.parse(aliceName)
        val bobX500Name = MemberX500Name.parse(bobName)
        val charlieX500Name = MemberX500Name.parse(charlieName)
        const val groupId = "7c5d6948-e17b-44e7-9d1c-fa4a3f667cad"
        val aliceHoldingIdentity = HoldingIdentity(aliceX500Name, groupId)
        val bobHoldingIdentity = HoldingIdentity(bobX500Name, groupId)

        fun Publisher.publishRawGroupPolicyData(
            virtualNodeInfoReader: VirtualNodeInfoReadService,
            cpiInfoReadService: CpiInfoReadService,
            holdingIdentity: HoldingIdentity,
            cryptoConnectionId: UUID,
            groupPolicy: String = sampleGroupPolicy1
        ) {
            val cpiVersion = UUID.randomUUID().toString()
            val previous = getVirtualNodeInfo(virtualNodeInfoReader, holdingIdentity)
            val previousCpiInfo = getCpiInfo(cpiInfoReadService, previous?.cpiIdentifier)
            // Create test data
            val cpiMetadata = getCpiMetadata(
                groupPolicy = groupPolicy,
                cpiVersion = cpiVersion
            )
            val virtualNodeInfo = VirtualNodeInfo(
                holdingIdentity = holdingIdentity,
                cpiIdentifier = cpiMetadata.cpiId,
                vaultDmlConnectionId = UUID.randomUUID(),
                cryptoDmlConnectionId = cryptoConnectionId,
                uniquenessDmlConnectionId = UUID.randomUUID(),
                timestamp = clock.instant()
            )

            // Publish test data
            publishCpiMetadata(cpiMetadata)

            eventually(
                duration = 20.seconds,
                waitBetween = 400.millis,
            ) {
                val newCpiInfo = getCpiInfo(cpiInfoReadService, cpiMetadata.cpiId)
                assertNotNull(newCpiInfo)
                assertNotEquals(previousCpiInfo, newCpiInfo)
                assertEquals(cpiVersion, newCpiInfo?.cpiId?.version)
            }

            publishVirtualNodeInfo(virtualNodeInfo)

            // wait for virtual node info reader to pick up changes
            eventually(
                duration = 20.seconds,
                waitBetween = 400.millis,
            ) {
                val newVNodeInfo = getVirtualNodeInfo(virtualNodeInfoReader, holdingIdentity)
                assertNotNull(newVNodeInfo)
                assertNotEquals(previous, newVNodeInfo)
                assertEquals(virtualNodeInfo.cpiIdentifier, newVNodeInfo?.cpiIdentifier)
            }
        }

        fun Lifecycle.startAndWait() {
            start()
            isStarted()
        }

        fun Lifecycle.isStarted() = eventually { assertTrue(isRunning) }

        val sampleGroupPolicy1 get() = getSampleGroupPolicy("/SampleGroupPolicy.json")
        val sampleGroupPolicy2 get() = getSampleGroupPolicy("/SampleGroupPolicy2.json")

        /**
         * Registration is not a call expected to happen repeatedly in quick succession so allowing more time in
         * between calls for a more realistic set up.
         */
        fun register(
            registrationProxy: RegistrationProxy,
            holdingIdentity: HoldingIdentity,
            publisher: Publisher,
        ) {
            val context = mapOf(KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME)
            return eventually(
                waitBetween = Duration.ofMillis(1000)
            ) {
                assertDoesNotThrow {
                    val records = registrationProxy.register(UUID.randomUUID(), holdingIdentity, context)
                    publisher.publish(records.toList())
                }
            }
        }

        fun assertLookupSize(groupReader: MembershipGroupReader, expectedSize: Int) = eventually {
            groupReader.lookup().also {
                assertEquals(expectedSize, it.size)
            }
        }

        fun lookup(groupReader: MembershipGroupReader, holdingIdentity: MemberX500Name) = eventually {
            val lookupResult = groupReader.lookup(holdingIdentity)
            assertNotNull(lookupResult)
            lookupResult!!
        }

        fun lookupFails(groupReader: MembershipGroupReader, holdingIdentity: MemberX500Name) = eventually {
            val lookupResult = groupReader.lookup(holdingIdentity)
            assertNull(lookupResult)
        }

        fun lookUpBySessionKey(groupReader: MembershipGroupReader, member: MemberInfo?) = eventually {
            val result = member?.let {
                groupReader.lookupBySessionKey(it.sessionInitiationKey.calculateHash())
            }
            assertNotNull(result)
            result!!
        }

        fun getGroupPolicyFails(
            groupPolicyProvider: GroupPolicyProvider,
            holdingIdentity: HoldingIdentity
        ) = eventually {
            val gp = assertDoesNotThrow { groupPolicyProvider.getGroupPolicy(holdingIdentity) }
            assertThat(gp).isNull()
        }

        fun getGroupPolicy(
            groupPolicyProvider: GroupPolicyProvider,
            holdingIdentity: HoldingIdentity
        ) = eventually {
            val policy = assertDoesNotThrow { groupPolicyProvider.getGroupPolicy(holdingIdentity) }
            assertThat(policy).isNotNull
            policy!!
        }

        fun assertGroupPolicy(new: GroupPolicy?, old: GroupPolicy? = null) {
            assertNotNull(new)
            old?.let {
                assertNotEquals(new, it)
            }
            assertEquals(groupId, new!!.groupId)
        }

        fun assertSecondGroupPolicy(new: GroupPolicy?, old: GroupPolicy?) {
            assertNotNull(new)
            assertNotNull(old)
            assertNotEquals(new, old)
            assertEquals("8a5d6947-e17b-44e7-9d1c-fa4a3f667abc", new!!.groupId)
        }

        fun Publisher.publishMessagingConf(messagingConfig: SmartConfig) =
            publishConf(ConfigKeys.MESSAGING_CONFIG, messagingConfig.root().render())

        fun Publisher.publishMembershipConf(membershipConfig: SmartConfig) =
            publishConf(ConfigKeys.MEMBERSHIP_CONFIG, membershipConfig.root().render())

        fun Publisher.publishDefaultCryptoConf(cryptoConfig: SmartConfig) =
            publishConf(ConfigKeys.CRYPTO_CONFIG, cryptoConfig.root().render())

        fun Publisher.publishGatewayConfig() =
            publishConf(
                ConfigKeys.P2P_GATEWAY_CONFIG,
                """
            sslConfig {
                tlsType: "ONE_WAY"
            }
        """
            )

        private fun getSampleGroupPolicy(fileName: String): String {
            val url = this::class.java.getResource(fileName)
            requireNotNull(url)
            return url.readText()
        }

        private fun getCpiIdentifier(
            name: String = "INTEGRATION_TEST",
            version: String
        ) = CpiIdentifier(name, version, SecureHash.parse("SHA-256:0000000000000000"))

        private fun getCpiMetadata(
            cpiVersion: String,
            groupPolicy: String,
            cpiIdentifier: CpiIdentifier = getCpiIdentifier(version = cpiVersion)
        ) = CpiMetadata(
            cpiIdentifier,
            SecureHash.parse("SHA-256:0000000000000000"),
            emptyList(),
            groupPolicy,
            -1,
            clock.instant()
        )

        private fun getVirtualNodeInfo(
            virtualNodeInfoReader: VirtualNodeInfoReadService,
            holdingIdentity: HoldingIdentity
        ) =
            virtualNodeInfoReader.get(holdingIdentity)

        private fun getCpiInfo(cpiInfoReadService: CpiInfoReadService, cpiIdentifier: CpiIdentifier?) =
            when (cpiIdentifier) {
                null -> {
                    null
                }
                else -> {
                    cpiInfoReadService.get(cpiIdentifier)
                }
            }

        private fun Publisher.publishVirtualNodeInfo(virtualNodeInfo: VirtualNodeInfo) {
            publish(
                listOf(
                    Record(
                        Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
                        virtualNodeInfo.holdingIdentity.toAvro(),
                        virtualNodeInfo.toAvro()
                    )
                )
            )
        }

        private val schemaVersion = ConfigurationSchemaVersion(1, 0)
        private fun Publisher.publishConf(configKey: String, conf: String) =
            publishRecord(Schemas.Config.CONFIG_TOPIC, configKey, Configuration(conf, conf, 0, schemaVersion))

        private fun Publisher.publishCpiMetadata(cpiMetadata: CpiMetadata) =
            publishRecord(Schemas.VirtualNode.CPI_INFO_TOPIC, cpiMetadata.cpiId.toAvro(), cpiMetadata.toAvro())

        private fun <K : Any, V : Any> Publisher.publishRecord(topic: String, key: K, value: V) =
            publish(listOf(Record(topic, key, value)))
    }
}
