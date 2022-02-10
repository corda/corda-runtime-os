package net.corda.membership.impl.read

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.packaging.CPI
import net.corda.packaging.converters.toAvro
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.identity.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class MembershipGroupReaderProviderIntegrationTest {

    companion object {
        const val CLIENT_ID = "group-read-integration-test"

        val logger = contextLogger()
    }

    @InjectService(timeout = 4000L)
    lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider

    @InjectService(timeout = 5000L)
    lateinit var groupPolicyProvider: GroupPolicyProvider

    @InjectService(timeout = 5000L)
    lateinit var virtualNodeInfoReader: VirtualNodeInfoReadService

    @InjectService(timeout = 5000L)
    lateinit var cpiInfoReader: CpiInfoReadService

    @InjectService
    lateinit var configurationReadService: ConfigurationReadService

    @InjectService
    lateinit var publisherFactory: PublisherFactory

    private val aliceX500Name = "C=GB, L=London, O=Alice"
    private val aliceMemberName = MemberX500Name.parse(aliceX500Name)
    private val groupId = "ABC123"
    private val aliceHoldingIdentity = HoldingIdentity(aliceX500Name, groupId)
    private val bootConf = "instanceId=1"

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

    var setUpComplete = false

    private val startableServices
        get() = listOf(
            configurationReadService,
            groupPolicyProvider,
            virtualNodeInfoReader,
            cpiInfoReader
        )

    @BeforeEach
    fun setUp() {
        if (!setUpComplete) {
            publisherFactory.createPublisher(PublisherConfig("group-reader-integration-test", 1)).publish(
                listOf(
                    Record(
                        Schemas.Config.CONFIG_TOPIC,
                        ConfigKeys.MESSAGING_CONFIG,
                        Configuration(messagingConf, "1")
                    )
                )
            )

            startableServices.forEach { it.startAndWait() }
            // Set basic bootstrap config
            with(ConfigFactory.parseString(bootConf)) {
                configurationReadService.bootstrapConfig(
                    SmartConfigFactory.create(this).create(this)
                )
            }

            // Create test data
            val cpiMetadata = getCpiMetadata()
            val virtualNodeInfo = VirtualNodeInfo(aliceHoldingIdentity, cpiMetadata.id)

            // Publish test data
            with(publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))) {
                publishVirtualNodeInfo(virtualNodeInfo)
                publishCpiMetadata(cpiMetadata)
                publishMessagingConf()
            }

            // Wait for published content to be picked up by components.
            eventually { Assertions.assertNotNull(getVirtualNodeInfo()) }

            setUpComplete = true
        }
    }

    @Test
    fun `test membership group reader provider component`() {

        /**
         * Cannot get group reader before starting the component.
         */
        membershipGroupReaderProvider.failGetAliceGroupReader()
        membershipGroupReaderProvider.isStopped()

        /**
         * After starting the group reader component it's possible to get a group reader.
         */
        membershipGroupReaderProvider.startAndWait()
        val groupReader1 = membershipGroupReaderProvider.getAliceGroupReader()

        /**
         * Readers are cached and additional reads return the same instance.
         */
        val groupReader2 = membershipGroupReaderProvider.getAliceGroupReader()
        assertEquals(groupReader1, groupReader2)

        /**
         * Group readers can not be retrieved after component stops.
         */
        membershipGroupReaderProvider.stopAndWait()
        membershipGroupReaderProvider.failGetAliceGroupReader()

        /**
         * New instance is returned after provider starts again meaning the cache was cleared.
         */
        membershipGroupReaderProvider.startAndWait()
        val groupReader3 = membershipGroupReaderProvider.getAliceGroupReader()
        assertNotEquals(groupReader1, groupReader3)
        assertNotEquals(groupReader2, groupReader3)

        /**
         * Stopping dependency service configuration read service, stops group read provider component.
         */
        configurationReadService.stopAndWait()
        eventually { membershipGroupReaderProvider.failGetAliceGroupReader() }

        /**
         * Starting dependency service configuration read service, starts group read provider component.
         */
        configurationReadService.startAndWait()
        eventually {
            startableServices.all { it.isRunning }
            membershipGroupReaderProvider.getAliceGroupReader()
        }
    }

    private fun Lifecycle.startAndWait() {
        logger.info("Starting component.")
        start()
        eventually { assertTrue(isRunning) }
    }

    private fun Lifecycle.stopAndWait() {
        logger.info("Stopping component.")
        stop()
        eventually { isStopped() }
    }

    private fun Lifecycle.isStopped() {
        assertFalse(isRunning)
    }

    private fun MembershipGroupReaderProvider.getAliceGroupReader(): MembershipGroupReader {
        logger.info("Getting group reader for test.")
        return getGroupReader(aliceHoldingIdentity).also {
            assertEquals(groupId, it.groupId)
            assertEquals(aliceMemberName, it.owningMember)
        }
    }

    private fun MembershipGroupReaderProvider.failGetAliceGroupReader() {
        logger.info("Running test expecting exception to be thrown.")
        assertThrows<CordaRuntimeException> { getGroupReader(aliceHoldingIdentity) }
    }

    private val sampleGroupPolicy1 get() = getSampleGroupPolicy("/SampleGroupPolicy.json")
    private fun getVirtualNodeInfo() = virtualNodeInfoReader.get(aliceHoldingIdentity)

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
                    Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
                    virtualNodeInfo.holdingIdentity.toAvro(),
                    virtualNodeInfo.toAvro()
                )
            )
        )
    }

    private fun Publisher.publishCpiMetadata(cpiMetadata: CPI.Metadata) =
        publishRecord(Schemas.VirtualNode.CPI_INFO_TOPIC, cpiMetadata.id.toAvro(), cpiMetadata.toAvro())

    private fun Publisher.publishMessagingConf() =
        publishRecord(Schemas.Config.CONFIG_TOPIC, ConfigKeys.MESSAGING_CONFIG, Configuration(messagingConf, "1"))

    private fun <K : Any, V : Any> Publisher.publishRecord(topic: String, key: K, value: V) =
        publish(listOf(Record(topic, key, value)))
}