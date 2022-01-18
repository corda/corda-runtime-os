package net.corda.membership.grouppolicy.integration

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.membership.GroupPolicy
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.packaging.CPI
import net.corda.packaging.converters.toAvro
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.CPI_INFO_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG
import net.corda.test.util.eventually
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReaderComponent
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupPolicyProviderIntegrationTest {
    companion object {
        const val CLIENT_ID = "group-policy-integration-test"
    }

    @InjectService(timeout = 5000L)
    lateinit var groupPolicyProvider: GroupPolicyProvider

    @InjectService(timeout = 5000L)
    lateinit var virtualNodeInfoReader: VirtualNodeInfoReaderComponent

    @InjectService(timeout = 5000L)
    lateinit var cpiInfoReader: CpiInfoReadService

    @InjectService(timeout = 5000L)
    lateinit var configurationReadService: ConfigurationReadService

    @InjectService(timeout = 5000L)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 5000L)
    lateinit var smartConfigFactory: SmartConfigFactory

    lateinit var publisher: Publisher

    private val aliceX500Name = "C=GB, L=London, O=Alice"
    private val groupId = "ABC123"
    private val aliceHoldingIdentity = HoldingIdentity(aliceX500Name, groupId)
    private val invalidHoldingIdentity = HoldingIdentity("", groupId)

    private val startableServices
        get() = listOf(
            configurationReadService,
            groupPolicyProvider,
            virtualNodeInfoReader,
            cpiInfoReader
        )

    @BeforeEach
    fun setUp() {
        // start all required services
        startableServices.forEach { it.startAndWait() }

        // Set basic bootstrap config
        configurationReadService.bootstrapConfig(
            smartConfigFactory.create(ConfigFactory.parseString(bootConf))
        )

        // Create test data
        val cpiMetadata = getCpiMetadata()
        val virtualNodeInfo = VirtualNodeInfo(aliceHoldingIdentity, cpiMetadata.id)

        // Publish test data
        with(publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))) {
            publishVirtualNodeInfo(virtualNodeInfo)
            publishCpiMetadata(cpiMetadata)
            publishMessagingConf()
            publisher = this
        }

        // Wait for published content to be picked up by components.
        eventually { assertNotNull(getVirtualNodeInfo()) }
    }

    @Test
    fun `Get group policy`() {
        /**
         * Group policy can be retrieved for valid holding identity.
         */
        val groupPolicy1 = getGroupPolicy()
        assertGroupPolicy(groupPolicy1)

        /**
         * Additional reads return the same (cached) instance.
         */
        assertEquals(groupPolicy1, getGroupPolicy())

        /**
         * Get group policy fails for unknown holding identity.
         */
        getGroupPolicyFails(invalidHoldingIdentity)

        /**
         * Group policy fails to be read if the component stops.
         */
        groupPolicyProvider.stopAndWait()
        getGroupPolicyFails()

        /**
         * Cache is cleared after a restart (new instance is returned).
         */
        groupPolicyProvider.startAndWait()
        val groupPolicy2 = getGroupPolicy()
        assertGroupPolicy(groupPolicy2, groupPolicy1)

        /**
         * Group policy cannot be retrieved if virtual node info reader dependency component goes down.
         */
        virtualNodeInfoReader.stopAndWait()
        groupPolicyProvider.isStopped()
        getGroupPolicyFails()

        /**
         * Group policy can be read after virtual node info reader dependency component starts again.
         */
        virtualNodeInfoReader.startAndWait()
        groupPolicyProvider.isStarted()
        val groupPolicy3 = getGroupPolicy()
        assertGroupPolicy(groupPolicy3, groupPolicy2)

        /**
         * Group policy cannot be retrieved if CPI info reader dependency component goes down.
         */
        cpiInfoReader.stopAndWait()
        groupPolicyProvider.isStopped()
        getGroupPolicyFails()

        /**
         * Group policy can be read after CPI info reader dependency component starts again.
         */
        cpiInfoReader.startAndWait()
        groupPolicyProvider.isStarted()
        val groupPolicy4 = getGroupPolicy()
        assertGroupPolicy(groupPolicy4, groupPolicy2)

        /**
         * Group policy object is updated when CPI info changes.
         * Push new virtual node info including changed group policy file in the CPI.
         */
        val cpiIdentifier = getCpiIdentifier(version = "1.1")
        val cpiMetadata = getCpiMetadata(cpiIdentifier, sampleGroupPolicy2)

        val previous = getVirtualNodeInfo()
        with(publisher) {
            publishCpiMetadata(cpiMetadata)
            publishVirtualNodeInfo(VirtualNodeInfo(aliceHoldingIdentity, cpiIdentifier))
        }
        // wait for virtual node info reader to pick up changes
        eventually { assertNotEquals(previous, getVirtualNodeInfo()) }

        val groupPolicy5 = getGroupPolicy()
        assertSecondGroupPolicy(groupPolicy5, groupPolicy4)
    }

    private fun Lifecycle.stopAndWait() {
        stop()
        isStopped()
    }

    private fun Lifecycle.startAndWait() {
        start()
        isStarted()
    }

    private fun Lifecycle.isStopped() = eventually { assertFalse(isRunning) }

    private fun Lifecycle.isStarted() = eventually { assertTrue(isRunning) }

    private fun getGroupPolicyFails(
        holdingIdentity: HoldingIdentity = aliceHoldingIdentity
    ) = assertThrows<CordaRuntimeException> { getGroupPolicy(holdingIdentity) }

    private fun getGroupPolicy(
        holdingIdentity: HoldingIdentity = aliceHoldingIdentity
    ) = groupPolicyProvider.getGroupPolicy(holdingIdentity)

    private fun assertGroupPolicy(new: GroupPolicy, old: GroupPolicy? = null) {
        old?.let {
            assertNotEquals(new, it)
        }
        assertEquals(groupId, new.groupId)
        assertEquals(6, new.keys.size)
    }

    private fun assertSecondGroupPolicy(new: GroupPolicy, old: GroupPolicy) {
        assertNotEquals(new, old)
        assertEquals("DEF456", new.groupId)
        assertEquals(2, new.size)
    }

    private fun getVirtualNodeInfo() = virtualNodeInfoReader.get(aliceHoldingIdentity)

    private val bootConf = """
        instanceId=1
    """

    private val messagingConf = """
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

    private val sampleGroupPolicy1 get() = getSampleGroupPolicy("/SampleGroupPolicy.json")
    private val sampleGroupPolicy2 get() = getSampleGroupPolicy("/SampleGroupPolicy2.json")

    private fun getSampleGroupPolicy(fileName: String): String {
        val url = this::class.java.getResource(fileName)
        requireNotNull(url)
        return url.readText()
    }

    private fun getCpiIdentifier(
        name: String = "GROUP_POLICY_TEST",
        version: String = "1.0"
    ) = CPI.Identifier.newInstance(name, version)

    private fun getCpiMetadata(
        cpiIdentifier: CPI.Identifier = getCpiIdentifier(),
        groupPolicy: String = sampleGroupPolicy1
    ) = CPI.Metadata.newInstance(
        cpiIdentifier,
        SecureHash.create("SHA-256:0000000000000000"),
        emptyList(),
        groupPolicy
    )

    private fun Publisher.publishVirtualNodeInfo(virtualNodeInfo: VirtualNodeInfo) {
        publish(
            listOf(
                Record(
                    VIRTUAL_NODE_INFO_TOPIC,
                    virtualNodeInfo.holdingIdentity.toAvro(),
                    virtualNodeInfo.toAvro()
                )
            )
        )
    }

    private fun Publisher.publishCpiMetadata(cpiMetadata: CPI.Metadata) =
        publishRecord(CPI_INFO_TOPIC, cpiMetadata.id.toAvro(), cpiMetadata.toAvro())

    private fun Publisher.publishMessagingConf() =
        publishRecord(CONFIG_TOPIC, MESSAGING_CONFIG, Configuration(messagingConf, "1"))

    private fun <K : Any, V : Any> Publisher.publishRecord(topic: String, key: K, value: V) =
        publish(listOf(Record(topic, key, value)))
}