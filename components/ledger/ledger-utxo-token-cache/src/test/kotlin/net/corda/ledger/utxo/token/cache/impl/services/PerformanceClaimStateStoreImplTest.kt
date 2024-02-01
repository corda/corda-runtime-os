package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.data.ledger.utxo.token.selection.data.TokenClaim
import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import net.corda.ledger.utxo.token.cache.impl.TOKEN_POOL_CACHE_STATE
import net.corda.ledger.utxo.token.cache.services.PerformanceClaimStateStoreImpl
import net.corda.ledger.utxo.token.cache.services.TokenPoolCacheManager
import net.corda.ledger.utxo.token.cache.services.TokenPoolCacheStateSerializationImpl
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messagebus.kafka.serialization.CordaAvroSerializationFactoryImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import net.corda.utilities.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Timer
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.concurrent.withLock

class PerformanceClaimStateStoreImplTest {
    private val serialization =
        TokenPoolCacheStateSerializationImpl(CordaAvroSerializationFactoryImpl(AvroSchemaRegistryImpl()))
    private val now = Instant.ofEpochMilli(1)
    private val clock = mock<Clock>().apply { whenever(instant()).thenReturn(now) }
    private val tokenPoolCacheManager = mock<TokenPoolCacheManager>()
    private val baseState = State(
        POOL_KEY.toString(),
        serialization.serialize(TOKEN_POOL_CACHE_STATE),
        modifiedTime = now
    )

    @Test
    // @org.junit.jupiter.api.Disabled
    @Suppress("SpreadOperator")
    fun `concurrency simulation`() {
        /*
         * Simulate two threads attempting to mutate the state against two instances that share the same store
         * each thread has to add 500 claims to the state, at the end of the test we expect the state to be consistent
         * with no missing claims. this should exercise the concurrency checking and batching, we can count failures
         * and write attempts to assert both batching happened and concurrency checking happened
         */

        val slowStateManager = StateManagerSimulator(10).apply {
            this.create(listOf(baseState))
        }

        val instanceA = createTarget(slowStateManager)
        val instanceB = createTarget(slowStateManager)

        val claimCount = 100
        var instanceAClaims = (0..claimCount).map { createTokenClaim("A$it") }
        var instanceBClaims = (0..claimCount).map { createTokenClaim("B$it") }
        val allClaimIds = instanceAClaims.map { it.claimId } + instanceBClaims.map { it.claimId }

        var instanceASuccessCount = 0
        var instanceAFailCount = 0
        var instanceBSuccessCount = 0
        var instanceBFailCount = 0
        val allInstanceClaims = mutableListOf<CompletableFuture<Boolean>>()

        for (i in 0..claimCount) {
            val newAClaim = instanceAClaims[i]
            val newBClaim = instanceBClaims[i]
            val f1 = CompletableFuture.supplyAsync {
                println("Started - ${newAClaim.claimId}")
                var isComplete = false
                while (!isComplete) {
                    isComplete = instanceA.enqueueRequest { poolState ->
                        if (poolState.tokenClaims.any { it.claimId == newAClaim.claimId }) {
                            println("unexpected claim ${newAClaim.claimId}")
                        } else {
                            println("${newAClaim.claimId}")
                        }
                        poolState.tokenClaims = poolState.tokenClaims + newAClaim
                        poolState
                    }.get()

                    if (isComplete) {
                        println("Success - ${newAClaim.claimId}")
                        instanceASuccessCount++
                    } else {
                        println("Failed - ${newAClaim.claimId}")
                        instanceAFailCount++
                    }
                }
                true
            }
            allInstanceClaims.add(f1)

            val f2 = CompletableFuture.supplyAsync {
                println("Started - ${newBClaim.claimId}")
                var isComplete = false
                while (!isComplete) {
                    isComplete = instanceB.enqueueRequest { poolState ->
                        if (poolState.tokenClaims.any { it.claimId == newBClaim.claimId }) {
                            println("unexpected claim ${newBClaim.claimId}")
                        } else {
                            println("${newBClaim.claimId}")
                        }
                        poolState.tokenClaims = poolState.tokenClaims + newBClaim
                        poolState
                    }.get()
                    if (isComplete) {
                        println("Success - ${newBClaim.claimId}")
                        instanceBSuccessCount++
                    } else {
                        println("Failed - ${newBClaim.claimId}")
                        instanceBFailCount++
                    }
                }
                true
            }

            allInstanceClaims.add(f2)
        }

        Timer().scheduleAtFixedRate(0, 100) {
            println("A ($instanceASuccessCount,$instanceAFailCount)  B ($instanceBSuccessCount,$instanceBFailCount) ")
        }

        CompletableFuture.allOf(*allInstanceClaims.toTypedArray()).get()

        val endState = slowStateManager.get(listOf(POOL_KEY.toString())).map { it.value }.first()
        val pool = serialization.deserialize(endState.value)
        assertThat(pool.tokenClaims.map { it.claimId }).containsOnlyOnceElementsOf(allClaimIds)

        // We expect the available tokens cache to be cleared for each concurrency failure
        verify(tokenPoolCacheManager, atLeast(1)).removeAllTokensFromCache(POOL_KEY)

        println("Update Call Count: ${slowStateManager.updateCallCount}")
        println("Update Fail Count: ${slowStateManager.updateFailCount}")
        println("Instance A  Failures: $instanceAFailCount")
        println("Instance B  Failures: $instanceBFailCount")
    }

    private fun createTokenClaim(claimId: String): TokenClaim {
        return TokenClaim.newBuilder()
            .setClaimId(claimId)
            .setClaimedTokens(listOf())
            .build()
    }

    private fun createTarget(
        sm: StateManager
    ): PerformanceClaimStateStoreImpl {
        return PerformanceClaimStateStoreImpl(POOL_KEY, serialization, sm, tokenPoolCacheManager, clock)
    }

    class StateManagerSimulator(private val updateSleepTime: Long = 0) : StateManager {
        private val lock = ReentrantLock()
        private val store = mutableMapOf<String, State>()
        var updateCallCount = 0
        var updateFailCount = 0

        override val name = LifecycleCoordinatorName("StateManagerSimulator", UUID.randomUUID().toString())

        override fun create(states: Collection<State>): Set<String> {
            return lock.withLock {
                val invalidStates = states
                    .filter { store.containsKey(it.key) }
                    .map { it.key }.toSet()

                states
                    .filterNot { store.containsKey(it.key) }
                    .forEach {
                        store[it.key] = it
                    }

                invalidStates
            }
        }

        override fun get(keys: Collection<String>): Map<String, State> {
            return lock.withLock {
                keys.mapNotNull { store[it] }.associateBy { it.key }
            }
        }

        override fun update(states: Collection<State>): Map<String, State> {
            return lock.withLock {
                val invalidStates = mutableMapOf<String, State>()
                for (state in states) {
                    // If the state doesn't exist then it's invalid
                    // If the stat exists but the versions don't match it's invalid
                    if (!store.containsKey(state.key)) {
                        throw IllegalStateException()
                    } else if (store[state.key]!!.version != state.version) {
                        invalidStates[state.key] = store[state.key]!!
                    } else {
                        store[state.key] = state.copy(version = state.version + 1)
                    }
                }

                // simulate some latency in the update op
                Thread.sleep(updateSleepTime)
                invalidStates
            }
        }

        override fun delete(states: Collection<State>): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun createOperationGroup(): StateOperationGroup {
            TODO("Not yet implemented")
        }

        override fun updatedBetween(interval: IntervalFilter): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun findUpdatedBetweenWithMetadataFilter(
            intervalFilter: IntervalFilter,
            metadataFilter: MetadataFilter
        ): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun findUpdatedBetweenWithMetadataMatchingAll(
            intervalFilter: IntervalFilter,
            metadataFilters: Collection<MetadataFilter>
        ): Map<String, State> {
            TODO("Not yet implemented")
        }

        override fun findUpdatedBetweenWithMetadataMatchingAny(
            intervalFilter: IntervalFilter,
            metadataFilters: Collection<MetadataFilter>
        ): Map<String, State> {
            TODO("Not yet implemented")
        }

        override val isRunning: Boolean
            get() = true

        override fun start() {
        }

        override fun stop() {
        }
    }
}
