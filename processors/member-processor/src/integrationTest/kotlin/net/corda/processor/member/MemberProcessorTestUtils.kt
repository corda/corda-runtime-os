package net.corda.processor.member

import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import net.corda.lifecycle.Lifecycle
import net.corda.membership.GroupPolicy
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.test.util.eventually
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.assertThrows
import java.util.*

class MemberProcessorTestUtils {
    companion object {
        val bootConf = with(ConfigFactory.parseString("$INSTANCE_ID=1")) {
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
        val aliceX500Name = MemberX500Name.parse(aliceName)
        val bobName = "C=GB, L=London, O=Bob"
        val bobX500Name = MemberX500Name.parse(bobName)
        val charlieName = "C=GB, L=London, O=Charlie"
        val charlieX500Name = MemberX500Name.parse(charlieName)
        val groupId = "ABC123"
        val aliceHoldingIdentity = HoldingIdentity(aliceName, groupId)

        fun Publisher.publishRawGroupPolicyData(
            virtualNodeInfoReader: VirtualNodeInfoReadService,
            holdingIdentity: HoldingIdentity = aliceHoldingIdentity,
            groupPolicy: String = sampleGroupPolicy1,
            cpiVersion: String = "1.0"
        ) {
            val previous = getVirtualNodeInfo(virtualNodeInfoReader)
            // Create test data
            val cpiMetadata = getCpiMetadata(
                groupPolicy = groupPolicy,
                cpiVersion = cpiVersion
            )
            val virtualNodeInfo = VirtualNodeInfo(holdingIdentity, cpiMetadata.id,
                null, UUID.randomUUID(), null, UUID.randomUUID())

            // Publish test data
            publishCpiMetadata(cpiMetadata)
            publishVirtualNodeInfo(virtualNodeInfo)

            // wait for virtual node info reader to pick up changes
            eventually {
                val newVNodeInfo = getVirtualNodeInfo(virtualNodeInfoReader)
                assertNotEquals(previous, newVNodeInfo)
                newVNodeInfo
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

        fun Lifecycle.isStopped() = eventually { Assertions.assertFalse(isRunning) }

        fun Lifecycle.isStarted() = eventually { Assertions.assertTrue(isRunning) }

        val sampleGroupPolicy1 get() = getSampleGroupPolicy("/SampleGroupPolicy.json")
        val sampleGroupPolicy2 get() = getSampleGroupPolicy("/SampleGroupPolicy2.json")

        fun getGroupPolicyFails(
            groupPolicyProvider: GroupPolicyProvider,
            holdingIdentity: HoldingIdentity = aliceHoldingIdentity
        ) = assertThrows<CordaRuntimeException> {
            getGroupPolicy(groupPolicyProvider, holdingIdentity)
        }

        fun getGroupPolicy(
            groupPolicyProvider: GroupPolicyProvider,
            holdingIdentity: HoldingIdentity = aliceHoldingIdentity
        ) = groupPolicyProvider.getGroupPolicy(holdingIdentity)

        fun assertGroupPolicy(new: GroupPolicy, old: GroupPolicy? = null) {
            old?.let {
                assertNotEquals(new, it)
            }
            Assertions.assertEquals(groupId, new.groupId)
            Assertions.assertEquals(6, new.keys.size)
        }

        fun assertSecondGroupPolicy(new: GroupPolicy, old: GroupPolicy) {
            assertNotEquals(new, old)
            Assertions.assertEquals("DEF456", new.groupId)
            Assertions.assertEquals(2, new.size)
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
            publishRecord(Schemas.VirtualNode.CPI_INFO_TOPIC, cpiMetadata.id.toAvro(), cpiMetadata.toAvro())

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