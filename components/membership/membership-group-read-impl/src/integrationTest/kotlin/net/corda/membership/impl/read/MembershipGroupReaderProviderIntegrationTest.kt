package net.corda.membership.impl.read

import com.typesafe.config.ConfigFactory
import kotlin.reflect.KFunction
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigFactoryFactory
import net.corda.lifecycle.Lifecycle
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class, DBSetup::class)
class MembershipGroupReaderProviderIntegrationTest {

    companion object {
        const val CLIENT_ID = "group-read-integration-test"

        val logger = contextLogger()
    }

    @InjectService(timeout = 4000L)
    lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider

    @InjectService
    lateinit var configurationReadService: ConfigurationReadService

    @InjectService
    lateinit var publisherFactory: PublisherFactory

    private val aliceX500Name = "C=GB, L=London, O=Alice"
    private val aliceMemberName = MemberX500Name.parse(aliceX500Name)
    private val groupId = "ABC123"
    private val aliceHoldingIdentity = HoldingIdentity(aliceMemberName, groupId)
    private val bootConf = """
        $INSTANCE_ID=1
        $BUS_TYPE = INMEMORY
        """.trimIndent()

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
            configurationReadService
        )
    private val schemaVersion = ConfigurationSchemaVersion(1,0)


    @BeforeEach
    fun setUp() {
        // Set basic bootstrap config
        val bootConfig = with(ConfigFactory.parseString(bootConf)) {
            SmartConfigFactoryFactory.createWithoutSecurityServices().create(this)
        }

        if (!setUpComplete) {
            publisherFactory.createPublisher(
                PublisherConfig("group-reader-integration-test"),
                bootConfig
            ).publish(
                listOf(
                    Record(
                        Schemas.Config.CONFIG_TOPIC,
                        ConfigKeys.MESSAGING_CONFIG,
                        Configuration(messagingConf, messagingConf, 0, schemaVersion)
                    )
                )
            )[0]

            startableServices.forEach { it.startAndWait() }

            configurationReadService.bootstrapConfig(bootConfig)
        }

        // Publish test data
        with(publisherFactory.createPublisher(PublisherConfig(CLIENT_ID), bootConfig)) {
            publishMessagingConf()
        }

        setUpComplete = true
    }

    val tests = listOf(
        ::`Cannot get group reader before starting the component`,
        ::`After starting the group reader component it's possible to get a group reader`,
        ::`Readers are cached and additional reads return the same instance`,
        ::`Group readers can not be retrieved after component stops`,
        ::`New instance is returned after provider restarts meaning the cache was cleared`,
        ::`Stopping and starting dependency service configuration read service, stops and starts group read provider component`
    )

    fun `Cannot get group reader before starting the component`() {
        membershipGroupReaderProvider.failGetAliceGroupReader()
        membershipGroupReaderProvider.isStopped()
    }

    fun `After starting the group reader component it's possible to get a group reader`() {
        membershipGroupReaderProvider.startAndWait()
        membershipGroupReaderProvider.getAliceGroupReader()
    }

    fun `Readers are cached and additional reads return the same instance`() {
        val groupReader1 = membershipGroupReaderProvider.getAliceGroupReader()
        val groupReader2 = membershipGroupReaderProvider.getAliceGroupReader()
        assertEquals(groupReader1, groupReader2)
    }

    fun `Group readers can not be retrieved after component stops`() {
        membershipGroupReaderProvider.stopAndWait()
        membershipGroupReaderProvider.failGetAliceGroupReader()
        membershipGroupReaderProvider.startAndWait()
    }

    fun `New instance is returned after provider restarts meaning the cache was cleared`() {
        val groupReader1 = membershipGroupReaderProvider.getAliceGroupReader()
        membershipGroupReaderProvider.stopAndWait()
        membershipGroupReaderProvider.startAndWait()
        val groupReader2 = membershipGroupReaderProvider.getAliceGroupReader()
        assertNotEquals(groupReader1, groupReader2)
    }

    fun `Stopping and starting dependency service configuration read service, stops and starts group read provider component`() {
        configurationReadService.stopAndWait()
        eventually { membershipGroupReaderProvider.failGetAliceGroupReader() }

        configurationReadService.startAndWait()
        eventually { assertTrue(startableServices.all { it.isRunning }) }
        eventually { assertDoesNotThrow { membershipGroupReaderProvider.getAliceGroupReader() } }
    }

    fun runTest(testFunction: KFunction<Unit>) {
        logger.info("Running test: \"${testFunction.name}\"")
        testFunction.call()
    }

    @Test
    fun `Run all tests`() {
        logger.info("Running multiple member group reader related integration tests under one test run.")
        logger.info("Running ${MembershipGroupReaderProvider::class.simpleName} tests.")
        for (test in tests) {
            runTest(test)
        }
        logger.info("Finished test run.")
        logger.info("Ran ${tests.size} tests.")
    }

    private fun Lifecycle.startAndWait() {
        logger.info("Starting component ${this::class.java.simpleName}.")
        start()
        eventually { assertTrue(isRunning) }
    }

    private fun Lifecycle.stopAndWait() {
        logger.info("Stopping component ${this::class.java.simpleName}.")
        stop()
        isStopped()
    }

    private fun Lifecycle.isStopped() {
        eventually { assertFalse(isRunning) }
    }

    private fun MembershipGroupReaderProvider.getAliceGroupReader(): MembershipGroupReader {
        logger.info("Getting group reader for test.")
        return eventually {
            assertDoesNotThrow {
                getGroupReader(aliceHoldingIdentity)
            }
        }.also {
            assertEquals(groupId, it.groupId)
            assertEquals(aliceMemberName, it.owningMember)
        }
    }

    private fun MembershipGroupReaderProvider.failGetAliceGroupReader() {
        logger.info("Running test expecting exception to be thrown.")
        eventually {
            assertThrows<IllegalStateException> { getGroupReader(aliceHoldingIdentity) }
        }
    }

    private fun Publisher.publishMessagingConf() =
        publishRecord(Schemas.Config.CONFIG_TOPIC, ConfigKeys.MESSAGING_CONFIG,
            Configuration(messagingConf, messagingConf, 0, schemaVersion))

    private fun <K : Any, V : Any> Publisher.publishRecord(topic: String, key: K, value: V) =
        publish(listOf(Record(topic, key, value)))
}

