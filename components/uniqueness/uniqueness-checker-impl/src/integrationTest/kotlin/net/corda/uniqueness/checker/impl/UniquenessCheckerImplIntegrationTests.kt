package net.corda.uniqueness.checker.impl

import net.corda.crypto.testkit.SecureHashUtils
import net.corda.data.uniqueness.UniquenessCheckRequest
import net.corda.data.uniqueness.UniquenessCheckResponse
import net.corda.data.uniqueness.UniquenessCheckResultSuccess
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.testkit.DbUtils
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.impl.JpaEntitiesRegistryImpl
import net.corda.test.util.time.AutoTickTestClock
import net.corda.uniqueness.backingstore.impl.JPABackingStoreImpl
import net.corda.uniqueness.backingstore.jpa.datamodel.JPABackingStoreEntities
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.uniqueness.utils.UniquenessAssertions
import net.corda.v5.crypto.SecureHash
import org.apache.avro.AvroRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import javax.persistence.EntityManagerFactory

/**
 * Integration tests the batched uniqueness checker implementation with the JPA based backing store
 * implementation and an associated JPA compatible DB
 */
// TODO: Find an elegant way to avoid duplication of unit tests
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UniquenessCheckerImplIntegrationTests {

    private val entityManagerFactory: EntityManagerFactory
    private val dbConfig = DbUtils.getEntityManagerConfiguration("uniqueness_default")

    private companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/uniqueness/db.changelog-master.xml"
    }

    private val baseTime: Instant = Instant.EPOCH

    // We don't use Instant.MAX because this appears to cause a long overflow in Avro
    private val defaultTimeWindowUpperBound: Instant =
        LocalDate.of(2200, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

    private lateinit var testClock: AutoTickTestClock

    private lateinit var uniquenessChecker: UniquenessChecker

    private fun currentTime(): Instant = testClock.peekTime()

    private fun newRequestBuilder(txId: SecureHash = SecureHashUtils.randomSecureHash())
            : UniquenessCheckRequest.Builder =
        UniquenessCheckRequest.newBuilder(
            UniquenessCheckRequest(
                txId.toString(),
                emptyList(),
                emptyList(),
                0,
                null,
                defaultTimeWindowUpperBound
            )
        )

    private fun processRequests(vararg requests: UniquenessCheckRequest) =
        uniquenessChecker.processRequests(requests.asList())

    private fun generateUnspentStates(numOutputStates: Int): List<String> {
        val issueTxId = SecureHashUtils.randomSecureHash()
        val unspentStateRefs = LinkedList<String>()

        repeat(numOutputStates) {
            unspentStateRefs.push("${issueTxId}:${it}")
        }

        processRequests(
            newRequestBuilder(issueTxId)
                .setNumOutputStates(numOutputStates)
                .build()
        ).let { responses ->
            assertAll(
                { assertThat(responses).hasSize(1) },
                { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
            )
        }

        return unspentStateRefs
    }

    /**
     * Creates an in-memory database, applies the relevant migration scripts, and initialises
     * [entityManagerFactory].
     */
    init {
        entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
            "uniqueness_default",
            JPABackingStoreEntities.classes.toList(),
            dbConfig
        )
    }

    @BeforeEach
    private fun init() {
        /*
         * Specific clock values are important to our testing in some cases, so we use a mock time
         * facilities service which provides a clock starting at a known point in time (baseTime)
         * and will increment its current time by one second on each call. The current time can also
         * be  manipulated by tests directly via [MockTimeFacilities.advanceTime] to change this
         * between calls (e.g. to manipulate time window behavior)
         */
        testClock = AutoTickTestClock(baseTime, Duration.ofSeconds(1))

        val backingStore = JPABackingStoreImpl(
            mock(),
            JpaEntitiesRegistryImpl(),
            mock<DbConnectionManager>().apply {
                whenever(getOrCreateEntityManagerFactory(any(),any(),any())) doReturn entityManagerFactory
                whenever(getClusterDataSource()) doReturn dbConfig.dataSource
            }
        )

        uniquenessChecker = BatchedUniquenessCheckerImpl(
            mock(),
            testClock,
            backingStore)

        backingStore.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mock())
    }

    @Nested
    inner class MalformedRequests {
        @Test
        fun `Request is missing time window upper bound`() {
            assertThrows(AvroRuntimeException::class.java, {
                newRequestBuilder()
                    .setTimeWindowUpperBound(null)
                    .build()
            }, "Field timeWindowUpperBound type:LONG pos:5 does not accept null values")
        }

        @Test
        fun `Request contains a negative number of output states`() {
            processRequests(
                newRequestBuilder()
                    .setNumOutputStates(-1)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    {
                        UniquenessAssertions.assertMalformedRequestResponse(
                            responses[0], "Number of output states cannot be less than 0."
                        )
                    }
                )
            }
        }
    }

    @Nested
    inner class InputStates {
        @Test
        fun `Attempting to spend an unknown input state fails`() {
            val inputStateRef = "${SecureHashUtils.randomSecureHash()}:0"

            processRequests(
                newRequestBuilder()
                    .setInputStates(listOf(inputStateRef))
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    {
                        UniquenessAssertions.assertUnknownInputStateResponse(
                            responses[0],
                            listOf(inputStateRef)
                        )
                    }
                )
            }
        }

        @Test
        fun `Single tx and single state spend is successful`() {
            processRequests(
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(1))
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }
        }

        @Test
        fun `Single tx and multiple state spends is successful`() {

            processRequests(
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(7))
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }
        }

        @Test
        fun `Single tx and single input state spend retried in same batch is successful`() {
            val request = newRequestBuilder()
                .setInputStates(generateUnspentStates(1))
                .build()

            processRequests(
                request,
                request
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) },
                    // Responses equal (idempotency)
                    { assertEquals(responses[0], responses[1]) }
                )
            }
        }

        @Test
        fun `Single tx and single input state spend retried in different batch is successful`() {
            val request = newRequestBuilder()
                .setInputStates(generateUnspentStates(1))
                .build()

            var initialResponse: UniquenessCheckResponse? = null

            processRequests(
                request
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
                initialResponse = responses[0]
            }

            processRequests(
                request
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    // Responses equal (idempotency)
                    { assertEquals(initialResponse, responses[0]) }
                )
            }
        }

        @Test
        fun `Multiple txs spending single different input states in same batch is successful`() {
            val requests = List(5) {
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(1))
                    .build()
            }

            uniquenessChecker.processRequests(requests).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(5) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[2], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[3], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[4], testClock) },
                    // Check all tx ids match up to corresponding requests and commit timestamps
                    // are unique
                    {
                        assertIterableEquals(
                            requests.map { it.txId },
                            responses.map { it.txId })
                    },
                    { UniquenessAssertions.assertUniqueCommitTimestamps(responses) }
                )
            }
        }

        @Test
        fun `Multiple txs spending single different input states in different batches is successful`() {
            val requests = List(5) {
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(1))
                    .build()
            }

            val allResponses = LinkedList<UniquenessCheckResponse>()

            repeat(5) { count ->
                processRequests(requests[count]).also { responses ->
                    assertAll(
                        { assertThat(responses).hasSize(1) },
                        {
                            UniquenessAssertions.assertStandardSuccessResponse(
                                responses[0],
                                testClock
                            )
                        },
                        {
                            assertEquals(
                                requests[count].txId,
                                responses[0].txId
                            )
                        },
                    )
                }.also { responses ->
                    allResponses.add(responses[0])
                }
            }

            UniquenessAssertions.assertUniqueCommitTimestamps(allResponses)
        }

        @Test
        fun `Multiple txs spending multiple different input states in same batch is successful`() {
            val requests = listOf(
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(7))
                    .build(),
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(3))
                    .build(),
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(1))
                    .build()
            )

            uniquenessChecker.processRequests(requests).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(3) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[2], testClock) },
                    // Check all tx ids match up to corresponding requests and commit timestamps
                    // are unique
                    {
                        assertIterableEquals(
                            requests.map { it.txId },
                            responses.map { it.txId })
                    },
                    { UniquenessAssertions.assertUniqueCommitTimestamps(responses) }
                )
            }
        }

        @Test
        fun `Multiple txs spending multiple different input states in different batches is successful`() {
            val requests = listOf(
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(7))
                    .build(),
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(3))
                    .build(),
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(1))
                    .build()
            )

            val allResponses = LinkedList<UniquenessCheckResponse>()

            repeat(3) { count ->
                processRequests(requests[count]).also { responses ->
                    assertAll(
                        { assertThat(responses).hasSize(1) },
                        {
                            UniquenessAssertions.assertStandardSuccessResponse(
                                responses[0],
                                testClock
                            )
                        },
                        {
                            assertEquals(
                                requests[count].txId,
                                responses[0].txId
                            )
                        },
                    )
                }.also { responses ->
                    allResponses.add(responses[0])
                }
            }

            UniquenessAssertions.assertUniqueCommitTimestamps(allResponses)
        }

        @Test
        fun `Multiple txs spending single duplicate input state in same batch fails for second tx`() {

            val sharedState = generateUnspentStates(1)

            processRequests(
                newRequestBuilder()
                    .setInputStates(sharedState)
                    .build(),
                newRequestBuilder()
                    .setInputStates(sharedState)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    {
                        UniquenessAssertions.assertInputStateConflictResponse(
                            responses[1],
                            listOf(sharedState.single())
                        )
                    }
                )
            }
        }

        @Test
        fun `Multiple txs spending single duplicate input state in different batch fails for second tx`() {
            val sharedState = generateUnspentStates(1)

            processRequests(
                newRequestBuilder()
                    .setInputStates(sharedState)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }

            processRequests(
                newRequestBuilder()
                    .setInputStates(sharedState)
                    .build()
            ).let { responses ->
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
        fun `Multiple txs spending multiple duplicate input states in same batch fails for second tx`() {
            val sharedState = List(3) { generateUnspentStates(1).single() }

            processRequests(
                newRequestBuilder()
                    .setInputStates(
                        listOf(
                            sharedState[0],
                            sharedState[1],
                            generateUnspentStates(1).single()
                        )
                    )
                    .build(),
                newRequestBuilder()
                    .setInputStates(
                        listOf(
                            sharedState[0],
                            sharedState[2]
                        )
                    )
                    .build(),
                newRequestBuilder()
                    .setInputStates(
                        listOf(
                            sharedState[2],
                            sharedState[1],
                            sharedState[0]
                        )
                    )
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(3) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    {
                        UniquenessAssertions.assertInputStateConflictResponse(
                            responses[1],
                            listOf(sharedState[0])
                        )
                    },
                    {
                        UniquenessAssertions.assertInputStateConflictResponse(
                            responses[2],
                            listOf(sharedState[0], sharedState[1])
                        )
                    }
                )
            }
        }

        @Test
        fun `Multiple txs spending multiple duplicate input states in different batch fails for second tx`() {
            val sharedState = List(3) { generateUnspentStates(1).single() }

            processRequests(
                newRequestBuilder()
                    .setInputStates(
                        listOf(
                            sharedState[0],
                            sharedState[1],
                            generateUnspentStates(1).single()
                        )
                    )
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }

            processRequests(
                newRequestBuilder()
                    .setInputStates(
                        listOf(
                            sharedState[0],
                            sharedState[2]
                        )
                    )
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    {
                        UniquenessAssertions.assertInputStateConflictResponse(
                            responses[0],
                            listOf(sharedState[0])
                        )
                    }
                )
            }

            processRequests(
                newRequestBuilder()
                    .setInputStates(
                        listOf(
                            sharedState[2],
                            sharedState[1],
                            sharedState[0]
                        )
                    )
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    {
                        UniquenessAssertions.assertInputStateConflictResponse(
                            responses[0],
                            listOf(sharedState[0], sharedState[1])
                        )
                    }
                )
            }
        }
    }

    @Nested
    inner class ReferenceStates {
        @Test
        fun `Attempting to spend an unknown reference state fails`() {
            val referenceStateRef = "${SecureHashUtils.randomSecureHash()}:0"

            processRequests(
                newRequestBuilder()
                    .setReferenceStates(listOf(referenceStateRef))
                    .build()
            ).let { responses ->
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
        fun `Single tx, no input states, single ref state is successful`() {
            processRequests(
                newRequestBuilder()
                    .setReferenceStates(generateUnspentStates(1))
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }
        }

        @Test
        fun `Single tx, no input states, single ref state retried in same batch is successful`() {
            val request = newRequestBuilder()
                .setReferenceStates(generateUnspentStates(1))
                .build()

            processRequests(
                request,
                request
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) },
                    // Responses equal (idempotency)
                    { assertEquals(responses[0], responses[1]) }
                )
            }
        }

        @Test
        fun `Single tx, no input states, single ref state retried in different batch is successful`() {
            val request = newRequestBuilder()
                .setReferenceStates(generateUnspentStates(1))
                .build()

            var initialResponse: UniquenessCheckResponse? = null

            processRequests(request).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )

                initialResponse = responses[0]
            }

            processRequests(request).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    // Responses equal (idempotency)
                    { assertEquals(initialResponse, responses[0]) }
                )
            }
        }

        @Test
        fun `Multiple txs, no input states, single shared ref state in same batch is successful`() {
            val sharedState = generateUnspentStates(1)

            processRequests(
                newRequestBuilder()
                    .setReferenceStates(sharedState)
                    .build(),
                newRequestBuilder()
                    .setReferenceStates(sharedState)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) },
                    { UniquenessAssertions.assertUniqueCommitTimestamps(responses) }
                )
            }
        }

        @Test
        fun `Multiple txs, no input states, single shared ref state in different batch is successful`() {
            val sharedState = generateUnspentStates(1)

            val allResponses = LinkedList<UniquenessCheckResponse>()

            processRequests(
                newRequestBuilder()
                    .setReferenceStates(sharedState)
                    .build()
            ).also { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }.also { responses ->
                allResponses.add(responses[0])
            }

            processRequests(
                newRequestBuilder()
                    .setReferenceStates(sharedState)
                    .build()
            ).also { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }.also { responses ->
                allResponses.add(responses[0])
            }

            UniquenessAssertions.assertUniqueCommitTimestamps(allResponses)
        }

        @Test
        fun `Multiple txs, no input states, multiple distinct ref states in same batch is successful`() {
            processRequests(
                newRequestBuilder()
                    .setReferenceStates(generateUnspentStates(3))
                    .build(),
                newRequestBuilder()
                    .setReferenceStates(generateUnspentStates(1))
                    .build(),
                newRequestBuilder()
                    .setReferenceStates(generateUnspentStates(6))
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(3) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[2], testClock) },
                    { UniquenessAssertions.assertUniqueCommitTimestamps(responses) }
                )
            }
        }

        @Test
        fun `Multiple txs, no input states, multiple distinct ref states in different batch is successful`() {
            val allResponses = LinkedList<UniquenessCheckResponse>()

            processRequests(
                newRequestBuilder()
                    .setReferenceStates(generateUnspentStates(3))
                    .build()
            ).also { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }.also { responses ->
                allResponses.add(responses[0])
            }

            processRequests(
                newRequestBuilder()
                    .setReferenceStates(generateUnspentStates(1))
                    .build()
            ).also { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }.also { responses ->
                allResponses.add(responses[0])
            }

            processRequests(
                newRequestBuilder()
                    .setReferenceStates(generateUnspentStates(6))
                    .build()
            ).also { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }.also { responses ->
                allResponses.add(responses[0])
            }

            UniquenessAssertions.assertUniqueCommitTimestamps(allResponses)
        }

        @Test
        fun `Single tx with single input state, single ref state is successful`() {
            processRequests(
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(1))
                    .setReferenceStates(generateUnspentStates(1))
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }
        }

        @Test
        fun `Single tx with same state used for input and ref state is successful`() {
            val state = generateUnspentStates(1)

            processRequests(
                newRequestBuilder()
                    .setInputStates(state)
                    .setReferenceStates(state)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }
        }

        @Test
        fun `Single tx with already spent ref state fails`() {
            val spentState = generateUnspentStates(1)

            processRequests(
                newRequestBuilder()
                    .setInputStates(spentState)
                    .build()
            ).let { responses ->
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
            ).let { responses ->
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
        fun `Single tx with single ref state replayed after ref state spent is successful`() {
            val state1 = generateUnspentStates(1)
            val replayableRequest = newRequestBuilder()
                .setReferenceStates(state1)
                .build()

            var initialResponse: UniquenessCheckResponse? = null

            processRequests(replayableRequest).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
                initialResponse = responses[0]
            }

            processRequests(
                newRequestBuilder()
                    .setInputStates(state1)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }

            processRequests(replayableRequest).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    // Responses equal (idempotency)
                    { assertEquals(initialResponse, responses[0]) }
                )
            }
        }

        @Test
        fun `Two txs using each others input states as references in same batch passes for tx1, fails for tx2`() {
            val states = List(2) { generateUnspentStates(1) }

            processRequests(
                newRequestBuilder()
                    .setInputStates(states[0])
                    .setReferenceStates(states[1])
                    .build(),
                newRequestBuilder()
                    .setInputStates(states[1])
                    .setReferenceStates(states[0])
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    {
                        UniquenessAssertions.assertReferenceStateConflictResponse(
                            responses[1],
                            states[0]
                        )
                    }
                )
            }
        }

        @Test
        fun `Two txs using each others input states as references in different batch passes for tx1, fails for tx2`() {
            val states = List(2) { generateUnspentStates(1) }

            processRequests(
                newRequestBuilder()
                    .setInputStates(states[0])
                    .setReferenceStates(states[1])
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }

            processRequests(
                newRequestBuilder()
                    .setInputStates(states[1])
                    .setReferenceStates(states[0])
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    {
                        UniquenessAssertions.assertReferenceStateConflictResponse(
                            responses[0],
                            states[0]
                        )
                    }
                )
            }
        }
    }

    @Nested
    inner class OutputStates {
        @Test
        fun `Replaying an issuance transaction in same batch is successful`() {
            val issueTxId = SecureHashUtils.randomSecureHash()

            processRequests(
                newRequestBuilder(issueTxId)
                    .setNumOutputStates(3)
                    .build(),
                newRequestBuilder(issueTxId)
                    .setNumOutputStates(3)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) },
                    // Responses equal (idempotency)
                    { assertEquals(responses[0], responses[1]) }
                )
            }
        }

        @Test
        fun `Replaying an issuance transaction in different batch is successful`() {
            val issueTxId = SecureHashUtils.randomSecureHash()
            lateinit var initialResponse: UniquenessCheckResponse

            processRequests(
                newRequestBuilder(issueTxId)
                    .setNumOutputStates(3)
                    .build(),

                ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )

                initialResponse = responses[0]
            }

            processRequests(
                newRequestBuilder(issueTxId)
                    .setNumOutputStates(3)
                    .build(),
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    // Responses equal (idempotency)
                    { assertEquals(responses[0], initialResponse) }
                )
            }
        }

        @Test
        fun `Generation and subsequent spend of large number of states is successful`() {
            val issueTxId = SecureHashUtils.randomSecureHash()
            val numStates = Short.MAX_VALUE

            processRequests(
                newRequestBuilder(issueTxId)
                    .setNumOutputStates(numStates.toInt())
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }

            processRequests(
                newRequestBuilder()
                    .setInputStates(List(2048) { "${issueTxId}:${it}" })
                    .build(),
                newRequestBuilder()
                    .setInputStates(listOf("${issueTxId}:${Short.MAX_VALUE}"))
                    .build(),
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    {
                        UniquenessAssertions.assertUnknownInputStateResponse(
                            responses[1], listOf("${issueTxId}:${Short.MAX_VALUE}")
                        )
                    }
                )
            }
        }
    }

    @Nested
    inner class TimeWindows {
        @Test
        fun `Tx processed within time window bounds is successful`() {
            processRequests(
                newRequestBuilder()
                    .setTimeWindowLowerBound(currentTime().minusSeconds(10))
                    .setTimeWindowUpperBound(currentTime().plusSeconds(10))
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }
        }

        @Test
        fun `Tx processed within time window bounds and retried outside of bounds is successful`() {
            val request = newRequestBuilder()
                .setTimeWindowLowerBound(currentTime().minusSeconds(10))
                .setTimeWindowUpperBound(currentTime().plusSeconds(10))
                .build()

            var initialResponse: UniquenessCheckResponse? = null

            processRequests(request).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
                initialResponse = responses[0]
            }

            // Move clock past window
            testClock.setTime(testClock.peekTime() + Duration.ofSeconds(100))

            processRequests(request).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    // Responses equal (idempotency)
                    { assertEquals(initialResponse, responses[0]) }
                )
            }
        }

        @Test
        fun `Tx processed before time window lower bound fails`() {
            val lowerBound = currentTime().plusSeconds(10)

            processRequests(
                newRequestBuilder()
                    .setTimeWindowLowerBound(lowerBound)
                    .build()
            ).let { responses ->
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
        fun `Tx processed before time window lower bound and retried after lower bound fails`() {
            val lowerBound = currentTime().plusSeconds(10)
            val request = newRequestBuilder()
                .setTimeWindowLowerBound(lowerBound)
                .build()
            var initialResponse: UniquenessCheckResponse? = null

            processRequests(request).let { responses ->
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
                initialResponse = responses[0]
            }

            // Tick up clock and retry
            testClock.setTime(testClock.peekTime() + Duration.ofSeconds(100))

            processRequests(request).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    {
                        UniquenessAssertions.assertTimeWindowOutOfBoundsResponse(
                            responses[0],
                            expectedLowerBound = lowerBound,
                            expectedUpperBound = defaultTimeWindowUpperBound
                        )
                    },
                    // Responses equal (idempotency)
                    { assertEquals(initialResponse, responses[0]) }
                )
                initialResponse = responses[0]
            }
        }

        @Test
        fun `Tx processed after time window upper bound fails`() {
            val upperBound = currentTime().minusSeconds(10)

            processRequests(
                newRequestBuilder()
                    .setTimeWindowUpperBound(upperBound)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    {
                        UniquenessAssertions.assertTimeWindowOutOfBoundsResponse(
                            responses[0], expectedUpperBound = upperBound
                        )
                    }
                )
            }
        }
    }

    @Nested
    inner class Miscellaneous {
        @Test
        fun `Empty request list returns no results`() {
            assertEquals(
                emptyList<UniquenessCheckResponse>(),
                uniquenessChecker.processRequests(emptyList())
            )
        }

        @Test
        fun `The same hash code produced by different algorithms are distinct states`() {
            val randomBytes = SecureHashUtils.randomBytes()
            val hash1 = SecureHash("SHA-256", randomBytes)
            val hash2 = SecureHash("SHA-512", randomBytes)
            val hash3 = SecureHash("SHAKE256", randomBytes)

            processRequests(
                newRequestBuilder(hash1)
                    .setNumOutputStates(1)
                    .build(),
                newRequestBuilder(hash2)
                    .setNumOutputStates(1)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) },
                    { UniquenessAssertions.assertUniqueCommitTimestamps(responses) }
                )
            }

            processRequests(
                newRequestBuilder()
                    .setInputStates(listOf("${hash1}:0"))
                    .build(),
                newRequestBuilder()
                    .setInputStates(listOf("${hash2}:0"))
                    .build(),
                newRequestBuilder()
                    .setInputStates(listOf("${hash3}:0"))
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(3) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) },
                    {
                        UniquenessAssertions.assertUnknownInputStateResponse(
                            responses[2],
                            listOf("${hash3}:0")
                        )
                    },
                    {
                        UniquenessAssertions.assertUniqueCommitTimestamps(
                            listOf(
                                responses[0],
                                responses[1]
                            )
                        )
                    }
                )
            }
        }

        @Test
        fun `Issue and subsequent spend of same state in a batch is successful`() {
            val issueTxId = SecureHashUtils.randomSecureHash()

            processRequests(
                newRequestBuilder(issueTxId)
                    .setNumOutputStates(1)
                    .build(),
                newRequestBuilder()
                    .setInputStates(listOf("${issueTxId}:0"))
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) },
                    { UniquenessAssertions.assertUniqueCommitTimestamps(responses) }
                )
            }
        }

        @Test
        fun `Spend and subsequent issue of same state in a batch fails spend, succeeds issue`() {
            val issueTxId = SecureHashUtils.randomSecureHash()

            processRequests(
                newRequestBuilder()
                    .setInputStates(listOf("${issueTxId}:0"))
                    .build(),
                newRequestBuilder(issueTxId)
                    .setNumOutputStates(1)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    {
                        UniquenessAssertions.assertUnknownInputStateResponse(
                            responses[0],
                            listOf("${issueTxId}:0")
                        )
                    },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) }
                )
            }
        }

        @Test
        fun `Tx failing input state, reference state and time window checks fails on input state check`() {
            // Initial tx to spend an input and reference state
            val states = List(2) { generateUnspentStates(1).single() }

            processRequests(
                newRequestBuilder()
                    .setInputStates(states)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }

            processRequests(
                newRequestBuilder()
                    .setInputStates(listOf(states[0]))
                    .setReferenceStates(listOf(states[1]))
                    .setTimeWindowLowerBound(currentTime().plusSeconds(10))
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    {
                        UniquenessAssertions.assertInputStateConflictResponse(
                            responses[0],
                            listOf(states[0])
                        )
                    }
                )
            }
        }

        @Test
        fun `Tx passing input state, failing reference state and time window checks fails on reference state check`() {
            // Initial tx to spend a reference state
            val state = generateUnspentStates(1)

            processRequests(
                newRequestBuilder()
                    .setInputStates(state)
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) }
                )
            }

            processRequests(
                newRequestBuilder()
                    .setInputStates(generateUnspentStates(1))
                    .setReferenceStates(state)
                    .setTimeWindowLowerBound(currentTime().plusSeconds(10))
                    .build()
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(1) },
                    {
                        UniquenessAssertions.assertReferenceStateConflictResponse(
                            responses[0],
                            state
                        )
                    }
                )
            }
        }

        @Test
        fun `Complex test scenario with multiple successes and failures in one batch`() {
            val priorSpentStates = List(2) { generateUnspentStates(1).single() }

            uniquenessChecker.processRequests(
                priorSpentStates.map {
                    newRequestBuilder()
                        .setInputStates(listOf(it))
                        .build()
                }
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[1], testClock) },
                    { UniquenessAssertions.assertUniqueCommitTimestamps(responses) }
                )
            }

            val retryableSuccessfulRequest = newRequestBuilder()
                .setInputStates(generateUnspentStates(100))
                .setReferenceStates(generateUnspentStates(20))
                .setTimeWindowLowerBound(currentTime())
                .setTimeWindowUpperBound(currentTime().plusSeconds(100))
                .build()

            val retryableFailedRequest = newRequestBuilder()
                .setInputStates(generateUnspentStates(1))
                .setReferenceStates(
                    generateUnspentStates(10) + priorSpentStates[0]
                )
                .build()

            var initialRetryableSuccessfulRequestResponse: UniquenessCheckResponse? = null
            var initialRetryableFailedRequestResponse: UniquenessCheckResponse? = null

            processRequests(retryableSuccessfulRequest, retryableFailedRequest).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(2) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    {
                        UniquenessAssertions.assertReferenceStateConflictResponse(
                            responses[1], listOf(priorSpentStates[0])
                        )
                    }
                )
                initialRetryableSuccessfulRequestResponse = responses[0]
                initialRetryableFailedRequestResponse = responses[1]
            }

            val doubleSpendAttemptStates = generateUnspentStates(3)

            val timeWindowUpperBound = currentTime().plusSeconds(1)

            processRequests(
                newRequestBuilder()
                    .setInputStates(
                        listOf(
                            generateUnspentStates(1).single(),
                            doubleSpendAttemptStates[0],
                            doubleSpendAttemptStates[1],
                            doubleSpendAttemptStates[2]
                        )
                    )
                    .setTimeWindowLowerBound(currentTime())
                    .build(),
                newRequestBuilder()
                    .setInputStates(
                        listOf(
                            generateUnspentStates(1).single(),
                            doubleSpendAttemptStates[0],
                            doubleSpendAttemptStates[1]
                        )
                    )
                    .setReferenceStates(
                        listOf(doubleSpendAttemptStates[2])
                    )
                    .build(),
                newRequestBuilder()
                    .setReferenceStates(
                        listOf(doubleSpendAttemptStates[2])
                    )
                    .build(),
                newRequestBuilder()
                    .setInputStates(
                        generateUnspentStates(10) +
                                priorSpentStates[0] +
                                priorSpentStates[1]
                    )
                    .build(),
                retryableFailedRequest,
                retryableSuccessfulRequest,
                newRequestBuilder()
                    .setTimeWindowUpperBound(timeWindowUpperBound)
                    .build(),
                *Array(3) {
                    newRequestBuilder()
                        .setInputStates(generateUnspentStates(3))
                        .setReferenceStates(generateUnspentStates(4))
                        .build()
                }
            ).let { responses ->
                assertAll(
                    { assertThat(responses).hasSize(10) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[0], testClock) },
                    {
                        UniquenessAssertions.assertInputStateConflictResponse(
                            responses[1],
                            listOf(doubleSpendAttemptStates[0], doubleSpendAttemptStates[1])
                        )
                    },
                    {
                        UniquenessAssertions.assertReferenceStateConflictResponse(
                            responses[2],
                            listOf(doubleSpendAttemptStates[2])
                        )
                    },
                    {
                        UniquenessAssertions.assertInputStateConflictResponse(
                            responses[3],
                            listOf(priorSpentStates[0], priorSpentStates[1])
                        )
                    },
                    {
                        assertEquals(
                            initialRetryableFailedRequestResponse,
                            responses[4]
                        )
                    },
                    {
                        assertEquals(
                            initialRetryableSuccessfulRequestResponse,
                            responses[5]
                        )
                    },
                    {
                        UniquenessAssertions.assertTimeWindowOutOfBoundsResponse(
                            responses[6], expectedUpperBound = timeWindowUpperBound
                        )
                    },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[7], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[8], testClock) },
                    { UniquenessAssertions.assertStandardSuccessResponse(responses[9], testClock) },
                    {
                        UniquenessAssertions.assertUniqueCommitTimestamps(responses.filter {
                            it.result is UniquenessCheckResultSuccess
                        })
                    }
                )
            }
        }
    }
}
