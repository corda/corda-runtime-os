package net.corda.processor.member

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.crypto.impl.config.addDefaultBootCryptoConfig
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.GroupPolicy
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.RegistrationProxy
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.lang.IllegalStateException
import java.util.UUID
import net.corda.test.util.time.TestClock
import java.time.Instant

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

        fun makeMessagingConfig(boostrapConfig: SmartConfig): SmartConfig =
            boostrapConfig.factory.create(
                ConfigFactory.parseString(MESSAGING_CONFIGURATION_VALUE)
                    .withFallback(ConfigFactory.parseString(BOOT_CONFIGURATION))
            )

        fun makeBootstrapConfig(extra: Map<String, SmartConfig>): SmartConfig {
            var cfg = SmartConfigFactory.create(
                ConfigFactory.parseString(
                    """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=passphrase
            ${SmartConfigFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
                )
            ).create(
                ConfigFactory
                    .parseString(MESSAGING_CONFIGURATION_VALUE)
                    .withFallback(
                        ConfigFactory.parseString(BOOT_CONFIGURATION)
                    )
            ).addDefaultBootCryptoConfig(
                fallbackCryptoRootKey = KeyCredentials("root-passphrase", "root-salt"),
                fallbackSoftKey = KeyCredentials("soft-passphrase", "soft-salt")
            )
            extra.forEach {
                cfg = cfg.withFallback(cfg.withValue(it.key, ConfigValueFactory.fromMap(it.value.root().unwrapped())))
            }
            return cfg
        }

        val aliceName = "C=GB, L=London, O=Alice"
        val bobName = "C=GB, L=London, O=Bob"
        val charlieName = "C=GB, L=London, O=Charlie"

        val aliceX500Name = MemberX500Name.parse(aliceName)
        val bobX500Name = MemberX500Name.parse(bobName)
        val charlieX500Name = MemberX500Name.parse(charlieName)
        val groupId = "ABC123"
        val aliceHoldingIdentity = HoldingIdentity(aliceX500Name.toString(), groupId)
        val bobHoldingIdentity = HoldingIdentity(bobX500Name.toString(), groupId)

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
                timestamp = clock.instant()
            )

            // Publish test data
            publishCpiMetadata(cpiMetadata)

            eventually {
                val newCpiInfo = getCpiInfo(cpiInfoReadService, cpiMetadata.cpiId)
                assertNotNull(newCpiInfo)
                assertNotEquals(previousCpiInfo, newCpiInfo)
                assertEquals(cpiVersion, newCpiInfo?.cpiId?.version)
            }

            publishVirtualNodeInfo(virtualNodeInfo)

            // wait for virtual node info reader to pick up changes
            eventually {
                val newVNodeInfo = getVirtualNodeInfo(virtualNodeInfoReader, holdingIdentity)
                assertNotNull(newVNodeInfo)
                assertNotEquals(previous, newVNodeInfo)
                assertEquals(virtualNodeInfo.cpiIdentifier, newVNodeInfo?.cpiIdentifier)
            }
        }

        fun Lifecycle.stopAndWait() {
            stop()
            isStopped()
        }

        fun Lifecycle.startAndWait() {
            start()
            isStarted()
        }

        fun Lifecycle.isStopped() = eventually { assertFalse(isRunning) }

        fun Lifecycle.isStarted() = eventually { assertTrue(isRunning) }

        val sampleGroupPolicy1 get() = getSampleGroupPolicy("/SampleGroupPolicy.json")
        val sampleGroupPolicy2 get() = getSampleGroupPolicy("/SampleGroupPolicy2.json")

        /**
         * Registration is not a call expected to happen repeatedly in quick succession so allowing more time in
         * between calls for a more realistic set up.
         */
        fun getRegistrationResult(
            registrationProxy: RegistrationProxy,
            holdingIdentity: HoldingIdentity
        ): MembershipRequestRegistrationResult =
            eventually(
                waitBetween = Duration.ofMillis(1000)
            ) {
                assertDoesNotThrow {
                    registrationProxy.register(holdingIdentity)
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

        fun lookUpFromPublicKey(groupReader: MembershipGroupReader, member: MemberInfo?) = eventually {
            val result = groupReader.lookup(PublicKeyHash.calculate(member!!.sessionInitiationKey))
            assertNotNull(result)
            result!!
        }

        fun getGroupPolicyFails(
            groupPolicyProvider: GroupPolicyProvider,
            holdingIdentity: HoldingIdentity,
            expectedException: Class<out Exception> = IllegalStateException::class.java
        ) = eventually {
            val e = assertThrows<Exception> { groupPolicyProvider.getGroupPolicy(holdingIdentity) }
            assertTrue(expectedException.isAssignableFrom(e::class.java))
        }

        fun getGroupPolicy(
            groupPolicyProvider: GroupPolicyProvider,
            holdingIdentity: HoldingIdentity
        ) = eventually {
            assertDoesNotThrow { groupPolicyProvider.getGroupPolicy(holdingIdentity) }
        }

        fun assertGroupPolicy(new: GroupPolicy?, old: GroupPolicy? = null) {
            assertNotNull(new)
            old?.let {
                assertNotEquals(new, it)
            }
            assertEquals(groupId, new!!.groupId)
            assertEquals(5, new.keys.size)
        }

        fun assertSecondGroupPolicy(new: GroupPolicy?, old: GroupPolicy?) {
            assertNotNull(new)
            assertNotNull(old)
            assertNotEquals(new, old)
            assertEquals("DEF456", new!!.groupId)
            assertEquals(2, new.size)
        }

        fun Publisher.publishMessagingConf(messagingConfig: SmartConfig) =
            publishConf(ConfigKeys.MESSAGING_CONFIG, messagingConfig.root().render())

        private fun getSampleGroupPolicy(fileName: String): String {
            val url = this::class.java.getResource(fileName)
            requireNotNull(url)
            return url.readText()
        }

        private fun getCpiIdentifier(
            name: String = "INTEGRATION_TEST",
            version: String
        ) = CpiIdentifier(name, version, SecureHash.create("SHA-256:0000000000000000"))

        private fun getCpiMetadata(
            cpiVersion: String,
            groupPolicy: String,
            cpiIdentifier: CpiIdentifier = getCpiIdentifier(version = cpiVersion)
        ) = CpiMetadata(
            cpiIdentifier,
            SecureHash.create("SHA-256:0000000000000000"),
            emptyList(),
            groupPolicy,
            -1,
            clock.instant()
        )

        private fun getVirtualNodeInfo(virtualNodeInfoReader: VirtualNodeInfoReadService, holdingIdentity: HoldingIdentity) =
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

        private val schemaVersion = ConfigurationSchemaVersion(1,0)
        private fun Publisher.publishConf(configKey: String, conf: String) =
            publishRecord(Schemas.Config.CONFIG_TOPIC, configKey, Configuration(conf, 0, schemaVersion))

        private fun Publisher.publishCpiMetadata(cpiMetadata: CpiMetadata) =
            publishRecord(Schemas.VirtualNode.CPI_INFO_TOPIC, cpiMetadata.cpiId.toAvro(), cpiMetadata.toAvro())

        private fun <K : Any, V : Any> Publisher.publishRecord(topic: String, key: K, value: V) =
            publish(listOf(Record(topic, key, value)))
    }
}
