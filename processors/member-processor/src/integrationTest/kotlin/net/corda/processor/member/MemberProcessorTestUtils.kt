package net.corda.processor.member

import com.typesafe.config.ConfigFactory
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import net.corda.lifecycle.Lifecycle
import net.corda.membership.GroupPolicy
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.provider.RegistrationProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.base.exceptions.CordaRuntimeException
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
import java.lang.IllegalStateException
import java.util.UUID

class MemberProcessorTestUtils {
    companion object {
        val bootConf = with(ConfigFactory.parseString("instanceId=1")) {
            SmartConfigFactory.create(this).create(this)
        }

        val cryptoConf = ""

        val messagingConf = """
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

        val aliceName = "C=GB, L=London, O=Alice"
        val bobName = "C=GB, L=London, O=Bob"
        val charlieName = "C=GB, L=London, O=Charlie"

        val aliceX500Name = MemberX500Name.parse(aliceName)
        val bobX500Name = MemberX500Name.parse(bobName)
        val charlieX500Name = MemberX500Name.parse(charlieName)
        val groupId = "ABC123"
        val aliceHoldingIdentity = HoldingIdentity(aliceName, groupId)

        fun Publisher.publishRawGroupPolicyData(
            virtualNodeInfoReader: VirtualNodeInfoReadService,
            cpiInfoReadService: CpiInfoReadService,
            holdingIdentity: HoldingIdentity = aliceHoldingIdentity,
            groupPolicy: String = sampleGroupPolicy1
        ) {
            val cpiVersion = UUID.randomUUID().toString()
            val previous = getVirtualNodeInfo(virtualNodeInfoReader)
            val previousCpiInfo = getCpiInfo(cpiInfoReadService, previous?.cpiIdentifier)
            // Create test data
            val cpiMetadata = getCpiMetadata(
                groupPolicy = groupPolicy,
                cpiVersion = cpiVersion
            )
            val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, cpiMetadata.cpiId,
                null, UUID.randomUUID(), null, UUID.randomUUID())

            // Publish test data
            publishCpiMetadata(cpiMetadata)

            eventually {
                val newCpiInfo = getCpiInfo(cpiInfoReadService, cpiMetadata.cpiId)
                assertNotNull(newCpiInfo)
                assertNotEquals(previousCpiInfo, newCpiInfo!!)
                assertEquals(cpiVersion, newCpiInfo.cpiId.version)
            }

            publishVirtualNodeInfo(virtualNodeInfo)

            // wait for virtual node info reader to pick up changes
            eventually {
                getVirtualNodeInfo(virtualNodeInfoReader).also {
                    assertNotEquals(previous, it)
                    assertEquals(virtualNodeInfo.cpiIdentifier, it?.cpiIdentifier)
                }
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

        fun getRegistrationService(registrationProvider: RegistrationProvider): MemberRegistrationService = eventually {
            assertDoesNotThrow {
                registrationProvider.get(aliceHoldingIdentity)
            }.also {
                assertNotNull(it)
            }
        }

        fun getRegistrationServiceFails(registrationProvider: RegistrationProvider) = eventually {
            assertThrows<CordaRuntimeException> {
                registrationProvider.get(aliceHoldingIdentity)
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
            val result = groupReader.lookup(PublicKeyHash.calculate(member!!.owningKey))
            assertNotNull(result)
            result!!
        }

        fun getGroupPolicyFails(
            groupPolicyProvider: GroupPolicyProvider,
            holdingIdentity: HoldingIdentity = aliceHoldingIdentity,
            expectedException: Class<out Exception> = IllegalStateException::class.java
        ) = eventually {
            val e = assertThrows<Exception> { groupPolicyProvider.getGroupPolicy(holdingIdentity) }
            assertTrue(expectedException.isAssignableFrom(e::class.java))
        }

        fun getGroupPolicy(
            groupPolicyProvider: GroupPolicyProvider,
            holdingIdentity: HoldingIdentity = aliceHoldingIdentity
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


        fun getSampleGroupPolicy(fileName: String): String {
            val url = this::class.java.getResource(fileName)
            requireNotNull(url)
            return url.readText()
        }

        fun getCpiIdentifier(
            name: String = "INTEGRATION_TEST",
            version: String
        ) = CpiIdentifier(name, version, SecureHash.create("SHA-256:0000000000000000"))

        fun getCpiMetadata(
            cpiVersion: String,
            groupPolicy: String,
            cpiIdentifier: CpiIdentifier = getCpiIdentifier(version = cpiVersion)
        ) = CpiMetadata(
            cpiIdentifier,
            SecureHash.create("SHA-256:0000000000000000"),
            emptyList(),
            groupPolicy
        )

        fun getVirtualNodeInfo(virtualNodeInfoReader: VirtualNodeInfoReadService) =
            virtualNodeInfoReader.get(aliceHoldingIdentity)

        fun getCpiInfo(cpiInfoReadService: CpiInfoReadService, cpiIdentifier: CpiIdentifier?) =
            when (cpiIdentifier) {
                null -> { null }
                else -> { cpiInfoReadService.get(cpiIdentifier) }
            }

        fun Publisher.publishVirtualNodeInfo(virtualNodeInfo: VirtualNodeInfo) {
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

        fun Publisher.publishCpiMetadata(cpiMetadata: CpiMetadata) =
            publishRecord(Schemas.VirtualNode.CPI_INFO_TOPIC, cpiMetadata.cpiId.toAvro(), cpiMetadata.toAvro())

        fun Publisher.publishMessagingConf() =
            publishConf(ConfigKeys.MESSAGING_CONFIG, messagingConf)

        fun Publisher.publishCryptoConf() =
            publishConf(ConfigKeys.CRYPTO_CONFIG, cryptoConf)

        private fun Publisher.publishConf(configKey: String, conf: String) =
            publishRecord(Schemas.Config.CONFIG_TOPIC, configKey, Configuration(conf, "1"))

        fun <K : Any, V : Any> Publisher.publishRecord(topic: String, key: K, value: V) =
            publish(listOf(Record(topic, key, value)))
    }
}