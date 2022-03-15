package net.corda.uniqueness.checker.impl

import net.corda.data.uniqueness.*
import net.corda.uniqueness.checker.UniquenessChecker
import net.corda.v5.crypto.SecureHash
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UniquenessCheckerImplTests {

    /*
     * Specific clock values are important to our testing in some cases, so we use a custom clock
     * which starts at a known point in time (baseTime) and will increment its current time
     * (currentTime) by one second on each call. The currentTime variable can also be manipulated
     * by tests directly to change this between calls (e.g. to manipulate time window behavior)
     */
    private val baseTime = Instant.EPOCH
    // We don't use Instant.MAX because this appears to cause a long overflow in Avro
    private val defaultTimeWindowUpperBound =
        LocalDate.of(2200, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
    private lateinit var currentTime: Instant

    private val testClock = mock<Clock>().apply {
        whenever(instant()).doAnswer {
            currentTime = currentTime.plusSeconds(1); currentTime }
    }

    private lateinit var uniquenessChecker: UniquenessChecker

    @BeforeEach
    fun init() {
        currentTime = baseTime
        uniquenessChecker = InMemoryUniquenessCheckerImpl(mock(), testClock)
    }

    @Nested
    inner class InputStates {
        @Test
        fun `Single tx and single state spend is successful`() {
            processRequests(
                newRequest().withInputStates(generateUnspentStates(1))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }
        }

        @Test
        fun `Single tx and multiple state spends is successful`() {

            processRequests(
                newRequest().withInputStates(generateUnspentStates(7))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }
        }

        @Test
        fun `Single tx and single input state spend retried in same batch is successful`() {
            val request = newRequest().withInputStates(generateUnspentStates(1))

            processRequests(
                request,
                request
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(2)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertStandardSuccessResponse(responses[1]) },
                    // Responses equal (idempotency)
                    { assertEquals(responses[0], responses[1]) }
                )
            }
        }

        @Test
        fun `Single tx and single input state spend retried in different batch is successful`() {
            val request = newRequest().withInputStates(generateUnspentStates(1))
            var initialResponse: UniquenessCheckResponse? = null

            processRequests(
                request
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
                initialResponse = responses[0]
            }

            processRequests(
                request
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    // Responses equal (idempotency)
                    { assertEquals(initialResponse, responses[0]) }
                )
            }
        }

        @Test
        fun `Multiple txs spending single different input states in same batch is successful`() {
            val requests = List(5) { newRequest()
                .withInputStates(generateUnspentStates(1)) }

            uniquenessChecker.processRequests(requests).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(5)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertStandardSuccessResponse(responses[1]) },
                    { assertStandardSuccessResponse(responses[2]) },
                    { assertStandardSuccessResponse(responses[3]) },
                    { assertStandardSuccessResponse(responses[4]) },
                    // Check all tx ids match up to corresponding requests and commit timestamps
                    // are unique
                    { assertIterableEquals(requests.map { it.txId }, responses.map{ it.txId }) },
                    { assertUniqueCommitTimestamps(responses) }
                )
            }
        }

        @Test
        fun `Multiple txs spending single different input states in different batches is successful`() {
            val requests = List(5) { newRequest()
                .withInputStates(generateUnspentStates(1)) }
            val allResponses = LinkedList<UniquenessCheckResponse>()

            repeat(5) { count ->
                processRequests(requests[count]).also { responses ->
                    assertAll(
                        { assertThat(responses, hasSize(1)) },
                        { assertStandardSuccessResponse(responses[0]) },
                        { assertEquals(requests[count].txId, responses[0].txId) },
                     )
                }.also { responses ->
                    allResponses.add(responses[0])
                }
            }

            assertUniqueCommitTimestamps(allResponses)
        }

        @Test
        fun `Multiple txs spending multiple different input states in same batch is successful`() {
            val requests = listOf(
                newRequest().withInputStates(generateUnspentStates(7)),
                newRequest().withInputStates(generateUnspentStates(3)),
                newRequest().withInputStates(generateUnspentStates(1))
            )

            uniquenessChecker.processRequests(requests).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(3)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertStandardSuccessResponse(responses[1]) },
                    { assertStandardSuccessResponse(responses[2]) },
                    // Check all tx ids match up to corresponding requests and commit timestamps
                    // are unique
                    { assertIterableEquals(requests.map { it.txId }, responses.map{ it.txId }) },
                    { assertUniqueCommitTimestamps(responses) }
                )
            }
        }

        @Test
        fun `Multiple txs spending multiple different input states in different batches is successful`() {
            val requests = listOf(
                newRequest().withInputStates(generateUnspentStates(7)),
                newRequest().withInputStates(generateUnspentStates(3)),
                newRequest().withInputStates(generateUnspentStates(1))
            )

            val allResponses = LinkedList<UniquenessCheckResponse>()

            repeat(3) { count ->
                processRequests(requests[count]).also { responses ->
                    assertAll(
                        { assertThat(responses, hasSize(1)) },
                        { assertStandardSuccessResponse(responses[0]) },
                        { assertEquals(requests[count].txId, responses[0].txId) },
                    )
                }.also { responses ->
                    allResponses.add(responses[0])
                }
            }

            assertUniqueCommitTimestamps(allResponses)
        }

        @Test
        fun `Multiple txs spending single duplicate input state in same batch fails for second tx`() {

            val sharedState = generateUnspentStates(1)

            processRequests(
                newRequest().withInputStates(sharedState),
                newRequest().withInputStates(sharedState)
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(2)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertInputStateConflictResponse(responses[1], listOf(sharedState.single()))}
                )
            }
        }

        @Test
        fun `Multiple txs spending single duplicate input state in different batch fails for second tx`() {
            val sharedState = generateUnspentStates(1)

            processRequests(
                newRequest().withInputStates(sharedState)
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }

            processRequests(
                newRequest().withInputStates(sharedState)
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertInputStateConflictResponse(responses[0], listOf(sharedState.single()))}
                )
            }
        }

        @Test
        fun `Multiple txs spending multiple duplicate input states in same batch fails for second tx`() {
            val sharedState = List(3) { generateUnspentStates(1).single() }

            processRequests(
                newRequest().withInputStates(listOf(
                    sharedState[0],
                    sharedState[1],
                    generateUnspentStates(1).single()
                )),
                newRequest().withInputStates(listOf(
                    sharedState[0],
                    sharedState[2]
                )),
                newRequest().withInputStates(listOf(
                    sharedState[2],
                    sharedState[1],
                    sharedState[0]
                ))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(3)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertInputStateConflictResponse(responses[1], listOf(sharedState[0]))},
                    { assertInputStateConflictResponse(responses[2],
                        listOf(sharedState[0], sharedState[1]))}
                )
            }
        }

        @Test
        fun `Multiple txs spending multiple duplicate input states in different batch fails for second tx`() {
            val sharedState = List(3) { generateUnspentStates(1).single() }

            processRequests(
                newRequest().withInputStates(listOf(
                    sharedState[0],
                    sharedState[1],
                    generateUnspentStates(1).single()
                ))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }

            processRequests(
                newRequest().withInputStates(listOf(
                    sharedState[0],
                    sharedState[2]
                ))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertInputStateConflictResponse(responses[0], listOf(sharedState[0]))}
                )
            }

            processRequests(
                newRequest().withInputStates(listOf(
                    sharedState[2],
                    sharedState[1],
                    sharedState[0]
                ))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertInputStateConflictResponse(responses[0],
                        listOf(sharedState[0], sharedState[1]))}
                )
            }
        }
    }

    @Nested
    inner class ReferenceStates {
        @Test
        fun `Single tx, no input states, single ref state is successful`() {
            processRequests(
                newRequest().withReferenceStates(generateUnspentStates(1))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }
        }

        @Test
        fun `Single tx, no input states, single ref state retried in same batch is successful`() {
            val request = newRequest().withReferenceStates(generateUnspentStates(1))

            processRequests(
                request,
                request
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(2)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertStandardSuccessResponse(responses[1]) },
                    // Responses equal (idempotency)
                    { assertEquals(responses[0], responses[1]) }
                )
            }
        }


        @Test
        fun `Single tx, no input states, single ref state retried in different batch is successful`() {
            val request = newRequest().withReferenceStates(generateUnspentStates(1))

            var initialResponse: UniquenessCheckResponse? = null

            processRequests(request).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )

                initialResponse = responses[0]
            }

            processRequests(request).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    // Responses equal (idempotency)
                    { assertEquals(initialResponse, responses[0]) }
                )
            }
        }

        @Test
        fun `Multiple txs, no input states, single shared ref state in same batch is successful`() {
            val sharedState = generateUnspentStates(1)

            processRequests(
                newRequest().withReferenceStates(sharedState),
                newRequest().withReferenceStates(sharedState)
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(2)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertStandardSuccessResponse(responses[1]) },
                    { assertUniqueCommitTimestamps(responses) }
                )
            }
        }

        @Test
        fun `Multiple txs, no input states, single shared ref state in different batch is successful`() {
            val sharedState = generateUnspentStates(1)

            val allResponses = LinkedList<UniquenessCheckResponse>()

            processRequests(
                newRequest().withReferenceStates(sharedState)
            ).also { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }.also { responses ->
                allResponses.add(responses[0])
            }

            processRequests(
                newRequest().withReferenceStates(sharedState)
            ).also { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }.also { responses ->
                allResponses.add(responses[0])
            }

            assertUniqueCommitTimestamps(allResponses)
        }

        @Test
        fun `Multiple txs, no input states, multiple distinct ref states in same batch is successful`() {
            processRequests(
                newRequest().withReferenceStates(generateUnspentStates(3)),
                newRequest().withReferenceStates(generateUnspentStates(1)),
                newRequest().withReferenceStates(generateUnspentStates(6))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(3)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertStandardSuccessResponse(responses[1]) },
                    { assertStandardSuccessResponse(responses[2]) },
                    { assertUniqueCommitTimestamps(responses) }
                )
            }
        }

        @Test
        fun `Multiple txs, no input states, multiple distinct ref states in different batch is successful`() {
            val allResponses = LinkedList<UniquenessCheckResponse>()

            processRequests(
                newRequest().withReferenceStates(generateUnspentStates(3))
            ).also { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }.also { responses ->
                allResponses.add(responses[0])
            }


            processRequests(
                newRequest().withReferenceStates(generateUnspentStates(1))
            ).also { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }.also { responses ->
                allResponses.add(responses[0])
            }


            processRequests(
                 newRequest().withReferenceStates(generateUnspentStates(6)),
            ).also { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }.also { responses ->
                allResponses.add(responses[0])
            }

            assertUniqueCommitTimestamps(allResponses)
        }

        @Test
        fun `Single tx with single input state, single ref state is successful`() {
            processRequests(
                newRequest()
                    .withInputStates(generateUnspentStates(1))
                    .withReferenceStates(generateUnspentStates(1))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }
        }

        @Test
        fun `Single tx with same state used for input and ref state is successful`() {
            val state = generateUnspentStates(1)

            processRequests(
                newRequest()
                    .withInputStates(state)
                    .withReferenceStates(state)
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }
        }

        @Test
        fun `Single tx with already spent ref state fails`() {
            val spentState = generateUnspentStates(1)

            processRequests(
                newRequest().withInputStates(spentState)
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }

            processRequests(
                newRequest()
                    .withInputStates(generateUnspentStates(1))
                    .withReferenceStates(spentState)
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertReferenceStateConflictResponse(responses[0], spentState) }
                )
            }
        }

        @Test
        fun `Single tx with single ref state replayed after ref state spent is successful`() {
            val state1 = generateUnspentStates(1)
            val replayableRequest = newRequest().withReferenceStates(state1)

            var initialResponse: UniquenessCheckResponse? = null

            processRequests(replayableRequest).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
                initialResponse = responses[0]
            }

            processRequests(
                newRequest()
                    .withInputStates(state1)
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }

            processRequests(replayableRequest).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    // Responses equal (idempotency)
                    { assertEquals(initialResponse, responses[0]) }
                )
            }
        }

        @Test
        fun `Two txs using each others input states as references in same batch passes for tx1, fails for tx2`() {
            val states = List(2) { generateUnspentStates(1) }

            processRequests(
                newRequest()
                    .withInputStates(states[0])
                    .withReferenceStates(states[1]),
                newRequest()
                    .withInputStates(states[1])
                    .withReferenceStates(states[0])
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(2)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertReferenceStateConflictResponse(responses[1], states[0])}
                )
            }
        }

        @Test
        fun `Two txs using each others input states as references in different batch passes for tx1, fails for tx2`() {
            val states = List(2) { generateUnspentStates(1) }

            processRequests(
                newRequest()
                    .withInputStates(states[0])
                    .withReferenceStates(states[1])
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }

            processRequests(
                newRequest()
                    .withInputStates(states[1])
                    .withReferenceStates(states[0])
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertReferenceStateConflictResponse(responses[0], states[0])}
                )
            }
        }
    }

    @Nested
    inner class TimeWindows {
        @Test
        fun `Tx processed within time window bounds is successful`() {
            processRequests(newRequest()
                .withTimeWindowLowerBound(currentTime.minusSeconds(10))
                .withTimeWindowUpperBound(currentTime.plusSeconds(10))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }
        }

        @Test
        fun `Tx processed within time window bounds and retried outside of bounds is successful`() {
            val request = newRequest()
                .withTimeWindowLowerBound(currentTime.minusSeconds(10))
                .withTimeWindowUpperBound(currentTime.plusSeconds(10))
            var initialResponse: UniquenessCheckResponse? = null

            processRequests(request).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
                initialResponse = responses[0]
            }

            // Move clock past window
            currentTime = currentTime.plusSeconds(100)

            processRequests(request).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    // Responses equal (idempotency)
                    { assertEquals(initialResponse, responses[0]) }
                )
            }
        }

        @Test
        fun `Tx processed before time window lower bound fails`() {
            val lowerBound = currentTime.plusSeconds(10)

            processRequests(newRequest().withTimeWindowLowerBound(lowerBound)).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertTimeWindowOutOfBoundsResponse(
                        responses[0], expectedLowerBound = lowerBound) }
                )
            }
        }

        @Test
        fun `Tx processed before time window lower bound and retried after lower bound fails`() {
            val lowerBound = currentTime.plusSeconds(10)
            val request = newRequest().withTimeWindowLowerBound(lowerBound)
            var initialResponse: UniquenessCheckResponse? = null

            processRequests(request).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertTimeWindowOutOfBoundsResponse(
                        responses[0], expectedLowerBound = lowerBound) }
                )
                initialResponse = responses[0]
            }

            // Tick up clock and retry
            currentTime = currentTime.plusSeconds(100)

            processRequests(request).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertTimeWindowOutOfBoundsResponse(
                        responses[0], expectedLowerBound = lowerBound) },
                    // Responses equal (idempotency)
                    { assertEquals(initialResponse, responses[0]) }
                )
                initialResponse = responses[0]
            }
        }

        @Test
        fun `Tx processed after time window upper bound fails`() {
            val upperBound = currentTime.minusSeconds(10)

            processRequests(newRequest().withTimeWindowUpperBound(upperBound)).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertTimeWindowOutOfBoundsResponse(
                        responses[0], expectedUpperBound = upperBound) }
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
        fun `Tx failing input state, reference state and time window checks fails on input state check`() {
            // Initial tx to spend an input and reference state
            val states = List(2) { generateUnspentStates(1).single() }

            processRequests(newRequest().withInputStates(states))
                .let { responses ->
                    assertAll(
                        { assertThat(responses, hasSize(1)) },
                        { assertStandardSuccessResponse(responses[0]) }
                )
            }

            processRequests(newRequest()
                .withInputStates(listOf(states[0]))
                .withReferenceStates(listOf(states[1]))
                .withTimeWindowLowerBound(currentTime.plusSeconds(10))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertInputStateConflictResponse(responses[0], listOf(states[0])) }
                )
            }
        }

        @Test
        fun `Tx passing input state, failing reference state and time window checks fails on reference state check`() {
            // Initial tx to spend a reference state
            val state = generateUnspentStates(1)

            processRequests(newRequest().withInputStates(state)).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertStandardSuccessResponse(responses[0]) }
                )
            }

            processRequests(newRequest()
                .withInputStates(generateUnspentStates(1))
                .withReferenceStates(state)
                .withTimeWindowLowerBound(currentTime.plusSeconds(10))
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(1)) },
                    { assertReferenceStateConflictResponse(responses[0], state) }
                )
            }
        }

        @Test
        fun `Complex test scenario with multiple successes and failures in one batch`() {
            val priorSpentStates = List(2) { generateUnspentStates(1).single() }

            uniquenessChecker.processRequests(
                priorSpentStates.map { newRequest().withInputStates(listOf(it)) }).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(2)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertStandardSuccessResponse(responses[1]) },
                    { assertUniqueCommitTimestamps(responses) }
                )
            }

            val retryableSuccessfulRequest = newRequest()
                .withInputStates(generateUnspentStates(100))
                .withReferenceStates(generateUnspentStates(20))
                .withTimeWindowLowerBound(currentTime)
                .withTimeWindowUpperBound(currentTime.plusSeconds(100))
            val retryableFailedRequest = newRequest()
                .withInputStates(generateUnspentStates(1))
                .withReferenceStates(
                    generateUnspentStates(10) + priorSpentStates[0])

            var initialRetryableSuccessfulRequestResponse: UniquenessCheckResponse? = null
            var initialRetryableFailedRequestResponse: UniquenessCheckResponse? = null

            processRequests(retryableSuccessfulRequest, retryableFailedRequest).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(2)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertReferenceStateConflictResponse(
                        responses[1], listOf(priorSpentStates[0])) }
                )
                initialRetryableSuccessfulRequestResponse = responses[0]
                initialRetryableFailedRequestResponse = responses[1]
            }

            val doubleSpendAttemptStates = generateUnspentStates(3)

            val timeWindowUpperBound = currentTime.plusSeconds(1)

            processRequests(
                newRequest()
                    .withInputStates(listOf(
                        generateUnspentStates(1).single(),
                        doubleSpendAttemptStates[0],
                        doubleSpendAttemptStates[1],
                        doubleSpendAttemptStates[2]
                    ))
                    .withTimeWindowLowerBound(currentTime),
                newRequest()
                    .withInputStates(listOf(
                        generateUnspentStates(1).single(),
                        doubleSpendAttemptStates[0],
                        doubleSpendAttemptStates[1]
                    ))
                    .withReferenceStates(
                        listOf(doubleSpendAttemptStates[2])),
                newRequest()
                    .withReferenceStates(
                        listOf(doubleSpendAttemptStates[2])),
                newRequest()
                    .withInputStates(
                        generateUnspentStates(10) +
                        priorSpentStates[0] +
                        priorSpentStates[1]
                    ),
                retryableFailedRequest,
                retryableSuccessfulRequest,
                newRequest().withTimeWindowUpperBound(timeWindowUpperBound),
                *Array(3) { newRequest()
                    .withInputStates(generateUnspentStates(3))
                    .withReferenceStates(generateUnspentStates(4)) }
            ).let { responses ->
                assertAll(
                    { assertThat(responses, hasSize(10)) },
                    { assertStandardSuccessResponse(responses[0]) },
                    { assertInputStateConflictResponse(responses[1],
                        listOf(doubleSpendAttemptStates[0], doubleSpendAttemptStates[1])) },
                    { assertReferenceStateConflictResponse(responses[2],
                        listOf(doubleSpendAttemptStates[2])) },
                    { assertInputStateConflictResponse(responses[3],
                        listOf(priorSpentStates[0], priorSpentStates[1])) },
                    { assertEquals(initialRetryableFailedRequestResponse, responses[4]) },
                    { assertEquals(initialRetryableSuccessfulRequestResponse, responses[5]) },
                    { assertTimeWindowOutOfBoundsResponse(
                        responses[6], expectedUpperBound = timeWindowUpperBound) },
                    { assertStandardSuccessResponse(responses[7]) },
                    { assertStandardSuccessResponse(responses[8]) },
                    { assertStandardSuccessResponse(responses[9]) },
                    { assertUniqueCommitTimestamps(responses.filter {
                        it.result is UniquenessCheckResultSuccess
                    }) }
                )
            }
        }
    }

    private fun processRequests(vararg requests: UniquenessCheckRequest) =
        uniquenessChecker.processRequests(requests.asList())

    private fun generateUnspentStates(numOutputStates: Int) : List<String> {
        val issueTxId = randomSecureHash()
        val unspentStateRefs = LinkedList<String>()

        repeat(numOutputStates) {
            unspentStateRefs.push("${issueTxId}:${it}")
        }

        processRequests(
            newRequest(issueTxId).withNumOutputStates(numOutputStates)
        ).let { responses ->
            assertAll(
                { assertThat(responses, hasSize(1)) },
                { assertStandardSuccessResponse(responses[0]) }
            )
        }

        return unspentStateRefs
    }

    private fun newRequest(txId: SecureHash = randomSecureHash()) = UniquenessCheckRequest(
        txId.toString(),
        emptyList(),
        emptyList(),
        0,
        null,
        defaultTimeWindowUpperBound)

    private fun UniquenessCheckRequest.withInputStates(inputStates: List<String>) =
        UniquenessCheckRequest.newBuilder(this).setInputStates(inputStates).build()

    private fun UniquenessCheckRequest.withReferenceStates(referenceStates: List<String>) =
        UniquenessCheckRequest.newBuilder(this).setReferenceStates(referenceStates).build()

    private fun UniquenessCheckRequest.withNumOutputStates(number: Int) =
        UniquenessCheckRequest.newBuilder(this).setNumOutputStates(number).build()

    private fun UniquenessCheckRequest.withTimeWindowLowerBound(time: Instant) =
        UniquenessCheckRequest.newBuilder(this).setTimeWindowLowerBound(time).build()

    private fun UniquenessCheckRequest.withTimeWindowUpperBound(time: Instant) =
        UniquenessCheckRequest.newBuilder(this).setTimeWindowUpperBound(time).build()

    // TODO: Copied from sandbox internal, should move to common test utils?
    private fun randomSecureHash(): SecureHash {
        val allowedChars = '0'..'9'
        val randomBytes = (1..16).map { allowedChars.random() }.joinToString("").toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        return SecureHash(digest.algorithm, digest.digest(randomBytes))
    }

    private fun<T> UniquenessCheckResponse.getResultOfType(expectedType: Class<T>) : T {
        assertInstanceOf(expectedType, this.result)
        @Suppress("UNCHECKED_CAST")
        return this.result as T
    }

    private fun assertStandardSuccessResponse(response: UniquenessCheckResponse) =
        response.getResultOfType(UniquenessCheckResultSuccess::class.java).run {
            assert(isWithinClockTimeRange(commitTimestamp))
        }

    private fun assertInputStateConflictResponse(
        response: UniquenessCheckResponse,
        expectedConflictingStates: List<String>) {
        response.getResultOfType(UniquenessCheckResultInputStateConflict::class.java).run {
            assertThat(conflictingStates, containsInAnyOrder(*expectedConflictingStates.toTypedArray()))
        }
    }

    private fun assertReferenceStateConflictResponse(
        response: UniquenessCheckResponse,
        expectedConflictingStates: List<String>) {
        response.getResultOfType(UniquenessCheckResultReferenceStateConflict::class.java).run {
            assertThat(conflictingStates, containsInAnyOrder(*expectedConflictingStates.toTypedArray()))
        }
    }

    private fun assertTimeWindowOutOfBoundsResponse(
        response: UniquenessCheckResponse,
        expectedLowerBound: Instant? = null,
        expectedUpperBound: Instant? = defaultTimeWindowUpperBound) {
        response.getResultOfType(UniquenessCheckResultTimeWindowOutOfBounds::class.java).run {
            assertAll(
                { assertEquals(expectedLowerBound, timeWindowLowerBound, "Lower bound") },
                { assertEquals(expectedUpperBound, timeWindowUpperBound, "Upper bound") }
            )
        }
    }

    private fun assertUniqueCommitTimestamps(responses: List<UniquenessCheckResponse>) {
        assertEquals(responses.size, responses.distinctBy {
            (it.result as UniquenessCheckResultSuccess).commitTimestamp }.size)
    }

    private fun isWithinClockTimeRange(time: Instant) =
        !time.isBefore(baseTime) && !time.isAfter(currentTime)
}
