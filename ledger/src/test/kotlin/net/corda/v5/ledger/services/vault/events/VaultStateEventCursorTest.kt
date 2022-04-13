package net.corda.v5.ledger.services.vault.events

import net.corda.v5.base.stream.Cursor.PollResult
import net.corda.v5.base.stream.Cursor.PollResult.PositionedValue
import net.corda.v5.base.stream.DurableCursor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.minutes
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.contracts.ContractState
import net.corda.v5.ledger.contracts.StateAndRef
import net.corda.v5.ledger.contracts.StateRef
import net.corda.v5.ledger.contracts.TransactionState
import net.corda.v5.ledger.identity.AbstractParty
import net.corda.v5.ledger.services.vault.VaultEventType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Tests the [DurableCursor] API tied to [VaultStateEventService] is usable from Kotlin.
 */
class VaultStateEventCursorTest {

    private companion object {
        val log = contextLogger()

        private fun createRandomHash(): SecureHash =
                SecureHash(DigestAlgorithmName.SHA2_256.name, ByteArray(32).apply {
                    SecureRandom().nextBytes(this)
                })
    }

    private val cursor: DurableCursor<VaultStateEvent<ContractState>> = mock()

    private class DummyState : ContractState {
        override val participants: List<AbstractParty> = emptyList()
    }

    private val result = createPollResult()

    private val latch = CountDownLatch(5)

    @Test
    fun `example kotlin usage`() {
        whenever(cursor.poll(50, 5.minutes)).thenReturn(result)
        val thread = thread(start = true) { run() }
        latch.await(5, TimeUnit.SECONDS)
        thread.interrupt()
    }

    @Suppress("UNCHECKED_CAST", "NestedBlockDepth")
    private fun run() {
        while (!Thread.currentThread().isInterrupted) {
            val result = cursor.poll(50, 5.minutes)
            if (!result.isEmpty) {
                for (positionedValue in result.positionedValues) {
                    log.info("Processing value: ${positionedValue.value} at position: ${positionedValue.position}")
                    val stateAndRef = positionedValue.value.stateAndRef
                    val state = stateAndRef.state.data
                    if (state is DummyState) {
                        doStuffWithState(state)
                        doStuffWithStateAndRef(stateAndRef as StateAndRef<DummyState>)
                    }
                    latch.countDown()
                }
                cursor.commit(result.lastPosition)
            }
        }
    }

    private fun doStuffWithState(state: DummyState) {
        log.info("Doing stuff with state: $state")
    }

    private fun doStuffWithStateAndRef(stateAndRef: StateAndRef<DummyState>) {
        log.info("Doing stuff with stateAndRef: $stateAndRef")
    }

    private fun createPollResult(): PollResult<VaultStateEvent<ContractState>> {
        return object : PollResult<VaultStateEvent<ContractState>> {
            override val positionedValues: List<PositionedValue<VaultStateEvent<ContractState>>> =
                listOf(object : PositionedValue<VaultStateEvent<ContractState>> {
                    override val value: VaultStateEvent<ContractState> = object : VaultStateEvent<ContractState> {
                        override val stateAndRef: StateAndRef<ContractState> = StateAndRef(
                            TransactionState(
                                DummyState(),
                                "",
                                mock()
                            ),
                            StateRef(createRandomHash(), 0)
                        )
                        override val eventType: VaultEventType = VaultEventType.PRODUCE
                        override val timestamp: Instant = Instant.now()
                    }
                    override val position: Long = 1
                })
            override val remainingElementsCountEstimate: Long = 1000
            override val isLastResult: Boolean = false
        }
    }
}