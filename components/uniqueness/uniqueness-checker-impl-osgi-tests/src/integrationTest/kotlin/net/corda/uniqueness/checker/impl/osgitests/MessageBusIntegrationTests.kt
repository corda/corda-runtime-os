package net.corda.uniqueness.checker.impl.osgitests

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.testkit.SecureHashUtils.randomSecureHash
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseError
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig
import net.corda.test.flow.external.events.TestExternalEventResponseMonitor
import net.corda.test.util.eventually
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.AutoTickTestClock
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.backingstore.impl.fake.BackingStoreImplFake
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.uniqueness.checker.impl.BatchedUniquenessCheckerImpl
import net.corda.uniqueness.utils.UniquenessAssertions
import net.corda.uniqueness.utils.UniquenessAssertions.assertUnknownInputStateResponse
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.lang.RuntimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

/**
 * Tests the integration of the uniqueness checker component with the message bus. This duplicates
 * a small subset of the tests in [UniquenessCheckerImplTests]
 * [net.corda.uniqueness.checker.impl.UniquenessCheckerImplTests] but uses a different
 * implementation of [processRequests] in order to send and receive using the message bus instead
 * of invoking the uniqueness checker directly. The tests also do not use a new uniqueness checker
 * instance on each test, reflecting a more realistic test setup where the same instance will be
 * used to process multiple batches of requests in sequence.
 *
 * These tests are not exhaustive, and are the minimum tests needed to verify each type of response
 * that can be returned to verify that payloads can be sent and received correctly. Tests also avoid
 * sending multiple requests that can impact each other, as unlike the unit tests, these could
 * behave non-deterministically as we cannot control the order in which requests are received by
 * the uniqueness checker.
 */
@ExtendWith(ServiceExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageBusIntegrationTests {
    private companion object {

        const val PUBLISHER_CLIENT_ID = "uniq-check-msg-bus-integ-test-pub"

        const val MESSAGING_CONFIG = """
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

        val logger = contextLogger()

        val bootConfig = SmartConfigFactory.create(ConfigFactory.empty())
            .create(
                ConfigFactory.parseString(
                    """
                ${BootConfig.INSTANCE_ID} = 1
                ${MessagingConfig.Bus.BUS_TYPE} = INMEMORY
                """
                )
            )
    }

    @InjectService(timeout = 5000)
    lateinit var configurationReadService: ConfigurationReadService

    @InjectService(timeout = 5000)
    lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

    @InjectService(timeout = 5000)
    lateinit var externalEventResponseFactory: ExternalEventResponseFactory

    @InjectService(timeout = 5000)
    lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

    @InjectService(timeout = 5000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 5000)
    lateinit var subscriptionFactory: SubscriptionFactory

    private lateinit var externalEventResponseMonitor: TestExternalEventResponseMonitor
    private lateinit var publisher: Publisher
    private lateinit var uniquenessChecker: UniquenessChecker

    /**
     * Behaves like [BackingStoreImplFake], but allows us to selectively force exceptions to be
     * thrown when creating new sessions.
     */
    private class ThrowableBackingStoreImplFake(
        coordinatorFactory: LifecycleCoordinatorFactory
    ) : BackingStoreImplFake(coordinatorFactory) {
        private var throwException: Boolean = false

        override fun session(
            holdingIdentity: HoldingIdentity,
            block: (BackingStore.Session) -> Unit
        ) {
            if ( throwException ) {
                throwException = false
                throw RuntimeException("Backing store forced to throw")
            } else {
                super.session(holdingIdentity, block)
            }
        }

        fun throwOnNextSession() { throwException = true }
    }

    private lateinit var backingStore: ThrowableBackingStoreImplFake

    private val groupId = UUID.randomUUID().toString()

    private val defaultHoldingIdentity = createTestHoldingIdentity(
        "C=GB, L=London, O=Alice", groupId).toAvro()

    // We don't use Instant.MAX because this appears to cause a long overflow in Avro
    private val defaultTimeWindowUpperBound: Instant =
        LocalDate.of(2200, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

    private val testClock = AutoTickTestClock(Instant.EPOCH, Duration.ofSeconds(1))

    private fun currentTime(): Instant = testClock.peekTime()

    private fun newRequestBuilder(txId: SecureHash = randomSecureHash())
            : UniquenessCheckRequestAvro.Builder =
        UniquenessCheckRequestAvro.newBuilder(
            UniquenessCheckRequestAvro(
                defaultHoldingIdentity,
                ExternalEventContext(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    KeyValuePairList(emptyList())
                ),
                txId.toString(),
                emptyList(),
                emptyList(),
                0,
                null,
                defaultTimeWindowUpperBound
            )
        )

    private fun processRequests(
        vararg requests: UniquenessCheckRequestAvro
    ) : List<ExternalEventResponse> {

        val requestIds = requests.map { it.flowExternalEventContext.requestId }

        val requestSerializer = cordaAvroSerializationFactory
            .createAvroSerializer<UniquenessCheckRequestAvro> { }

        val sendFuture = publisher.publish(
            requests.map { request ->
                Record(
                    Schemas.UniquenessChecker.UNIQUENESS_CHECK_TOPIC,
                    UUID.randomUUID().toString(),
                    requestSerializer.serialize(request)
                )
            }
        )

        eventually {
            assertThat(sendFuture)
                .hasSize(1)
                .allSatisfy {
                    assertThat(it).isCompletedWithValue(Unit)
                }
        }

        val responses = externalEventResponseMonitor.getResponses(requestIds)

        // Responses may be out of order with respect to the passed in request list, so re-sequence
        // these before returning
        return requestIds.map { responses[it]!! }
    }

    private fun ExternalEventResponse.toSuccessfulResponse(): UniquenessCheckResponseAvro {
        assertThat(this.payload).isNotNull

        val responseDeserializer = cordaAvroSerializationFactory
            .createAvroDeserializer({}, UniquenessCheckResponseAvro::class.java)

        return  responseDeserializer.deserialize(
            this.payload.array()) as UniquenessCheckResponseAvro
    }

    private fun ExternalEventResponse.toUnsuccessfulResponse(): ExternalEventResponseError {
        assertThat(this.error).isNotNull

        return this.error
    }

    private fun generateUnspentStates(numOutputStates: Int): List<String> {
        val issueTxId = randomSecureHash()
        val unspentStateRefs = LinkedList<String>()

        repeat(numOutputStates) {
            unspentStateRefs.push("$issueTxId:$it")
        }

        processRequests(
            newRequestBuilder(issueTxId)
                .setNumOutputStates(numOutputStates)
                .build()
        ).map { it.toSuccessfulResponse() }.let { responses ->
            assertAll(
                { assertThat(responses).hasSize(2) },
                { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
            )
        }

        return unspentStateRefs
    }

    @BeforeAll
    fun initialSetup() {
        backingStore = ThrowableBackingStoreImplFake(lifecycleCoordinatorFactory)
            .also { it.start() }

        uniquenessChecker = BatchedUniquenessCheckerImpl(
            lifecycleCoordinatorFactory,
            configurationReadService,
            subscriptionFactory,
            externalEventResponseFactory,
            testClock,
            backingStore
        ).also { it.start() }

        publisher = publisherFactory.createPublisher(
            PublisherConfig(PUBLISHER_CLIENT_ID),
            bootConfig
        ).also {
            it.start()
            it.publish(
                listOf(
                    Record(
                        Schemas.Config.CONFIG_TOPIC,
                        ConfigKeys.MESSAGING_CONFIG,
                        Configuration(
                            MESSAGING_CONFIG,
                            MESSAGING_CONFIG,
                            0,
                            ConfigurationSchemaVersion(1,0)
                        )
                    )
                )
            ).single()
        }

        configurationReadService.start()
        configurationReadService.bootstrapConfig(bootConfig)

        val coordinator = lifecycleCoordinatorFactory.createCoordinator<MessageBusIntegrationTests> { e, c ->
            if (e is StartEvent) {
                logger.info("Starting test coordinator")
                c.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<BackingStore>(),
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        LifecycleCoordinatorName.forComponent<UniquenessChecker>()
                    )
                )
            } else if (e is RegistrationStatusChangeEvent) {
                logger.info("Test coordinator is ${e.status}")
                c.updateStatus(e.status)
            }
        }.also { it.start() }

        externalEventResponseMonitor =
            TestExternalEventResponseMonitor(subscriptionFactory, bootConfig)

        eventually {
            logger.info("Waiting for required services to start...")
            assertThat(coordinator.status).isEqualTo(LifecycleStatus.UP)
            logger.info("Required services started.")
        }
    }

    @Test
    fun `single tx and single state spend is successful`() {
        processRequests(
            newRequestBuilder()
                .setInputStates(generateUnspentStates(1))
                .build()
        ).map { it.toSuccessfulResponse() }.let { responses ->
            assertAll(
                { assertThat(responses).hasSize(3) },
                { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
            )
        }
    }

    @Test
    fun `attempting to spend an unknown input state fails`() {
        val inputStateRef = "${randomSecureHash()}:0"

        processRequests(
            newRequestBuilder()
                .setInputStates(listOf(inputStateRef))
                .build()
        ).map { it.toSuccessfulResponse() }.let { responses ->
            assertAll(
                { assertThat(responses).hasSize(1) },
                { assertUnknownInputStateResponse(responses[0], listOf(inputStateRef)) }
            )
        }
    }

    @Test
    fun `multiple txs spending single duplicate input state in different batch fails for second tx`() {
        val sharedState = generateUnspentStates(1)

        processRequests(
            newRequestBuilder()
                .setInputStates(sharedState)
                .build()
        ).map { it.toSuccessfulResponse() }.let { responses ->
            assertAll(
                { assertThat(responses).hasSize(1) },
                { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
            )
        }

        processRequests(
            newRequestBuilder()
                .setInputStates(sharedState)
                .build()
        ).map { it.toSuccessfulResponse() }.let { responses ->
            assertAll(
                { assertThat(responses).hasSize(1) },
                {
                    UniquenessAssertions.assertInputStateConflictResponse(
                        responses[0],
                        listOf(sharedState.single())
                    )
                }
            )
        }
    }

    @Test
    fun `attempting to spend an unknown reference state fails`() {
        val referenceStateRef = "${randomSecureHash()}:0"

        processRequests(
            newRequestBuilder()
                .setReferenceStates(listOf(referenceStateRef))
                .build()
        ).map { it.toSuccessfulResponse() }.let { responses ->
            assertAll(
                { assertThat(responses).hasSize(1) },
                {
                    UniquenessAssertions.assertUnknownReferenceStateResponse(
                        responses[0],
                        listOf(referenceStateRef)
                    )
                }
            )
        }
    }

    @Test
    fun `single tx with already spent ref state fails`() {
        val spentState = generateUnspentStates(1)

        processRequests(
            newRequestBuilder()
                .setInputStates(spentState)
                .build()
        ).map { it.toSuccessfulResponse() }.let { responses ->
            assertAll(
                { assertThat(responses).hasSize(1) },
                { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
            )
        }

        processRequests(
            newRequestBuilder()
                .setInputStates(generateUnspentStates(1))
                .setReferenceStates(spentState)
                .build()
        ).map { it.toSuccessfulResponse() }.let { responses ->
            assertAll(
                { assertThat(responses).hasSize(1) },
                {
                    UniquenessAssertions.assertReferenceStateConflictResponse(
                        responses[0],
                        spentState
                    )
                }
            )
        }
    }

    @Test
    fun `tx processed before time window lower bound fails`() {
        val lowerBound = currentTime().plusSeconds(10)

        processRequests(
            newRequestBuilder()
                .setTimeWindowLowerBound(lowerBound)
                .build()
        ).map { it.toSuccessfulResponse() }.let { responses ->
            assertAll(
                { assertThat(responses).hasSize(1) },
                {
                    UniquenessAssertions.assertTimeWindowOutOfBoundsResponse(
                        responses[0],
                        expectedLowerBound = lowerBound,
                        expectedUpperBound = defaultTimeWindowUpperBound
                    )
                }
            )
        }
    }

    @Test
    fun `exception thrown during uniqueness checking raises a platform error`() {

        backingStore.throwOnNextSession()

        processRequests(
            newRequestBuilder()
                .setNumOutputStates(1)
                .build()
        ).map { it.toUnsuccessfulResponse() }.let { responses ->
            assertAll(
                { assertThat(responses).hasSize(1) },
                { assertThat(responses[0].errorType)
                    .isEqualTo(ExternalEventResponseErrorType.PLATFORM) },
                { assertThat(responses[0].exception.errorMessage)
                    .isEqualTo("Backing store forced to throw")}
            )
        }
    }
}
