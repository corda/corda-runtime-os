package net.corda.utxo.token.sync.integration.tests

import net.corda.data.ledger.utxo.token.selection.data.TokenCachedSyncCheck
import net.corda.data.ledger.utxo.token.selection.data.TokenFullSyncRequest
import net.corda.data.ledger.utxo.token.selection.data.TokenFullSyncState
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.data.TokenPoolPeriodicSyncState
import net.corda.data.ledger.utxo.token.selection.data.TokenSyncWakeUp
import net.corda.data.ledger.utxo.token.selection.event.TokenSyncEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncMode
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
import net.corda.schema.Schemas.Services.Companion.TOKEN_CACHE_EVENT
import net.corda.schema.Schemas.Services.Companion.TOKEN_CACHE_SYNC_EVENT
import net.corda.schema.configuration.LedgerConfig
import net.corda.utxo.token.sync.integration.tests.context.TestContextFactory
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CacheSyncComponentTest {

    private val virtualNodes = listOf(BOB_VIRTUAL_NODE, ALICE_VIRTUAL_NODE)
    private val testContextFactory = TestContextFactory()

    // Epoc is used as min value by the component,
    // so for our tests we have to use a value >  epoc
    private val initialSystemTime = Instant.EPOCH.plusSeconds(1)
    private val bobTokenPoolKey = TokenPoolCacheKey(
        BOB_SHORT_ID,
        "type-bob",
        "issuer",
        "notary",
        "symbol"
    )

    @BeforeAll
    fun setupBackground() {
        /**
         * To use Postgres rather than in-memory (HSQL):
         *
         *     docker run --rm --name test-instance -e POSTGRES_PASSWORD=password -p 5432:5432 postgres
         *     uncomment the line below
         */
         // System.setProperty("postgresPort", "5432")

        // Add the virtual nodes for the cluster
        testContextFactory.addVirtualNodes(
            listOf(
                BOB_VIRTUAL_NODE,
                ALICE_VIRTUAL_NODE
            )
        )

        // Create the vault dbs for the virtual nodes
        testContextFactory.createDb()

        // Add some tokens to Bob's vault
        testContextFactory.addTransaction(BOB_VIRTUAL_NODE, "tx1_bob")
        testContextFactory.addTokenAsOutputRecord(
            BOB_VIRTUAL_NODE,
            "tx1_bob",
            0,
            "type-bob",
            "issuer",
            "notary",
            "symbol",
            initialSystemTime
        )
        testContextFactory.addTokenAsOutputRecord(
            BOB_VIRTUAL_NODE,
            "tx1_bob",
            1,
            "type-bob",
            "issuer",
            "notary",
            "symbol",
            initialSystemTime.plusSeconds(1)
        )

        testContextFactory.addTransaction(BOB_VIRTUAL_NODE, "tx2_bob")
        testContextFactory.addTokenAsOutputRecord(
            BOB_VIRTUAL_NODE,
            "tx2_bob",
            0,
            "type-bob",
            "issuer",
            "notary",
            "symbol",
            initialSystemTime.plusSeconds(2)
        )
        testContextFactory.addTokenAsOutputRecord(
            BOB_VIRTUAL_NODE,
            "tx2_bob",
            1,
            "type-bob",
            "issuer",
            "notary",
            "symbol",
            initialSystemTime.plusSeconds(3)
        )

        // Add some tokens to Alice's vault
        testContextFactory.addTransaction(ALICE_VIRTUAL_NODE, "tx1_alice")
        testContextFactory.addTokenAsOutputRecord(
            ALICE_VIRTUAL_NODE,
            "tx1_alice",
            0,
            "type-alice-a",
            "issuer",
            "notary",
            "symbol",
            initialSystemTime
        )

        testContextFactory.addTransaction(ALICE_VIRTUAL_NODE, "tx2_alice")
        testContextFactory.addTokenAsOutputRecord(
            ALICE_VIRTUAL_NODE,
            "tx2_alice",
            0,
            "type-alice-b",
            "issuer",
            "notary",
            "symbol",
            initialSystemTime.plusSeconds(1)
        )
    }

    @Test
    fun `When the component starts - a wake-up event is published for all virtual nodes in the cluster`() {
        // When our component and supporting services are started
        val context = testContextFactory.createTestContext()
        context.startComponent()
        context.assertComponentHasStarted()

        // Then expect a wake-up event to be sent for every virtual node in the cluster
        context.assertTokenSyncWakUpReceived(TOKEN_CACHE_SYNC_EVENT, BOB_VIRTUAL_NODE.holdingIdentity)
        context.assertTokenSyncWakUpReceived(TOKEN_CACHE_SYNC_EVENT, ALICE_VIRTUAL_NODE.holdingIdentity)
    }

    @Test
    fun `When wake-up event received - no existing state - defaults to periodc check mode`() {
        // Given our component and supporting services are running
        val config = mapOf<String, Any>(LedgerConfig.UTXO_TOKEN_MIN_DELAY_BEFORE_NEXT_PERIODIC_SYNC to 10)

        val context = testContextFactory.createTestContext(config)
        context.setSystemTime(initialSystemTime)
        context.startComponent()
        context.assertComponentHasStarted()

        // When a wakeup event is sent for Alice
        val wakeUpEventRequest = TokenSyncEvent(
            ALICE_VIRTUAL_NODE.holdingIdentity.toAvro(),
            TokenSyncWakeUp()
        )

        context.publishEvent(
            TOKEN_CACHE_SYNC_EVENT,
            ALICE_VIRTUAL_NODE.holdingIdentity.shortHash.toString(),
            wakeUpEventRequest
        )

        // Then expect the state to be in periodic sync mode and the next block timestamps to be updated for each
        // token pool
        val aliceShortHoldingId = ALICE_VIRTUAL_NODE.holdingIdentity.shortHash.toString()
        val pool1 = TokenPoolCacheKey(aliceShortHoldingId, "type-alice-a", "issuer", "notary", "symbol")
        val pool2 = TokenPoolCacheKey(aliceShortHoldingId, "type-alice-b", "issuer", "notary", "symbol")

        val expectedTokenPoolPeriodicSyncState1 = TokenPoolPeriodicSyncState(pool1, initialSystemTime)
        val expectedTokenPoolPeriodicSyncState2 = TokenPoolPeriodicSyncState(pool2, initialSystemTime.plusSeconds(1))

        context.assertStateOnTopic(
            TOKEN_CACHE_SYNC_EVENT,
            ALICE_VIRTUAL_NODE.holdingIdentity,
            TokenSyncMode.PERIODIC_CHECK,
            null,
            listOf(expectedTokenPoolPeriodicSyncState1, expectedTokenPoolPeriodicSyncState2),
            initialSystemTime.plusSeconds(10),
            0
        )

        // And the sync check records should be output for the token pools
        context.assertOutputRecordCount(TOKEN_CACHE_EVENT, 2)

        context.assertTokenUnspentSyncCheckEventRecords(
            TOKEN_CACHE_EVENT,
            listOf(
                TokenCachedSyncCheck(pool1, listOf("tx1_alice:0")),
                TokenCachedSyncCheck(pool2, listOf("tx2_alice:0"))
            )
        )
    }

    /**
     * The period check process reads (per token pool) sequential blocks of records based on last modified timestamp
     * when a block is read we record the max last modified of the block and use that as the starting point of the next
     * block. The problem with this strategy is that, unlike a sequence number, we can't be sure we don't have two
     * records with the same timestamp so we when we select a block we need to select using
     * last_modified>= last_last_modified. this means we will always take at least the last record from the last block
     * again. this is not a problem for the sync check as it's just a bit of duplication, but in the test below it might
     * look odd if you're not aware of the mechanics at work.
     */
    @Test
    fun `When wake-up event received - periodic check mode - return sequential blocks of records`() {
        // Given our component and supporting services are running
        val config = mapOf<String, Any>(
            LedgerConfig.UTXO_TOKEN_MIN_DELAY_BEFORE_NEXT_PERIODIC_SYNC to 10,
            LedgerConfig.UTXO_TOKEN_PERIODIC_CHECK_BLOCK_SIZE to 2,
        )

        val context = testContextFactory.createTestContext(config)
        context.setSystemTime(initialSystemTime)
        context.startComponent()
        context.assertComponentHasStarted()

        // When a wakeup event is sent for Bob
        context.publishEvent(
            TOKEN_CACHE_SYNC_EVENT,
            BOB_SHORT_ID,
            TokenSyncEvent(BOB_VIRTUAL_NODE.holdingIdentity.toAvro(), TokenSyncWakeUp())
        )

        // Then expect the first two (configured block size) records to be read from the DB and published.
        context.assertOutputRecordCount(TOKEN_CACHE_EVENT, 1)

        context.assertTokenUnspentSyncCheckEventRecords(
            TOKEN_CACHE_EVENT,
            listOf(
                TokenCachedSyncCheck(bobTokenPoolKey, listOf("tx1_bob:0", "tx1_bob:1"))
            )
        )

        // And then for the second pass we expect to get the next block of records.
        // As described above this will include the last record of the first block.
        context.clearPublishedRecords(TOKEN_CACHE_EVENT)
        context.publishEvent(
            TOKEN_CACHE_SYNC_EVENT,
            BOB_SHORT_ID,
            TokenSyncEvent(BOB_VIRTUAL_NODE.holdingIdentity.toAvro(), TokenSyncWakeUp())
        )

        context.assertOutputRecordCount(TOKEN_CACHE_EVENT, 1)

        context.assertTokenUnspentSyncCheckEventRecords(
            TOKEN_CACHE_EVENT,
            listOf(
                TokenCachedSyncCheck(bobTokenPoolKey, listOf("tx1_bob:1", "tx2_bob:0"))
            )
        )
    }

    /*
     * The periodic check process reads blocks of records, ordered by last modified, with each run. when the process
     * has read all the current records it will start again at the beginning and repeat the process.
     */
    @Test
    fun `When wake-up event received - periodic check mode - when all records read then reset to the begining of the dataset`() {
        // Given our component and supporting services are running
        val config = mapOf<String, Any>(
            LedgerConfig.UTXO_TOKEN_MIN_DELAY_BEFORE_NEXT_PERIODIC_SYNC to 10,
            LedgerConfig.UTXO_TOKEN_PERIODIC_CHECK_BLOCK_SIZE to 3,
        )

        val context = testContextFactory.createTestContext(config)
        context.setSystemTime(initialSystemTime)
        context.startComponent()
        context.assertComponentHasStarted()

        // When a wakeup event is sent for Bob
        context.publishEvent(
            TOKEN_CACHE_SYNC_EVENT,
            BOB_SHORT_ID,
            TokenSyncEvent(BOB_VIRTUAL_NODE.holdingIdentity.toAvro(), TokenSyncWakeUp())
        )

        // Then expect the first three (configured block size) records to be read from the DB and published.
        context.assertOutputRecordCount(TOKEN_CACHE_EVENT, 1)

        context.assertTokenUnspentSyncCheckEventRecords(
            TOKEN_CACHE_EVENT,
            listOf(
                TokenCachedSyncCheck(bobTokenPoolKey, listOf("tx1_bob:0", "tx1_bob:1", "tx2_bob:0"))
            )
        )

        // And then for the second pass we expect to get a set of two records, the last record from the first
        // block and the remaining 4th record for bob
        context.clearPublishedRecords(TOKEN_CACHE_EVENT)
        context.publishEvent(
            TOKEN_CACHE_SYNC_EVENT,
            BOB_SHORT_ID,
            TokenSyncEvent(BOB_VIRTUAL_NODE.holdingIdentity.toAvro(), TokenSyncWakeUp())
        )

        context.assertOutputRecordCount(TOKEN_CACHE_EVENT, 1)

        context.assertTokenUnspentSyncCheckEventRecords(
            TOKEN_CACHE_EVENT,
            listOf(
                TokenCachedSyncCheck(bobTokenPoolKey, listOf("tx2_bob:0", "tx2_bob:1"))
            )
        )

        // And then we expect the processes to restart, and we should receive the first three records again
        context.clearPublishedRecords(TOKEN_CACHE_SYNC_EVENT)
        context.publishEvent(
            TOKEN_CACHE_SYNC_EVENT,
            BOB_SHORT_ID,
            TokenSyncEvent(BOB_VIRTUAL_NODE.holdingIdentity.toAvro(), TokenSyncWakeUp())
        )

        context.assertOutputRecordCount(TOKEN_CACHE_SYNC_EVENT, 1)

        context.assertTokenUnspentSyncCheckEventRecords(
            TOKEN_CACHE_EVENT,
            listOf(
                TokenCachedSyncCheck(bobTokenPoolKey, listOf("tx1_bob:0", "tx1_bob:1", "tx2_bob:0"))
            )
        )
    }

    @Test
    fun `When full sync event received - full sync mode - already running sync, ignore and do nothing`() {
        // Given our component and supporting services are running
        val config = mapOf<String, Any>(
            LedgerConfig.UTXO_TOKEN_FULL_SYNC_BLOCK_SIZE to 3,
        )

        val context = testContextFactory.createTestContext(config)
        context.setSystemTime(initialSystemTime)
        context.startComponent()
        context.assertComponentHasStarted()

        // And Given the sync state for the virtual node is already in full sync mode
        val bobShortId = BOB_VIRTUAL_NODE.holdingIdentity.shortHash.toString()
        val currentFullSyncState = TokenFullSyncState().apply {
            this.startedTimestamp = initialSystemTime
            this.lastBlockCompletedTimestamp = initialSystemTime
            this.blocksCompleted = 0;
            this.recordsCompleted = 0;
            this.nextBlockStartOffset = initialSystemTime
        }

        val currentSyncState = TokenSyncState().apply {
            holdingIdentity = BOB_VIRTUAL_NODE.holdingIdentity.toAvro()
            mode = TokenSyncMode.FULL_SYNC
            fullSyncState = currentFullSyncState
            periodicSyncState = listOf()
            nextWakeup = initialSystemTime
            transientFailureCount = 0
        }

        context.publishState(TOKEN_CACHE_SYNC_EVENT, bobShortId, currentSyncState)

        // When a full sync request event is sent for Bob
        context.publishEvent(
            TOKEN_CACHE_SYNC_EVENT,
            bobShortId,
            TokenSyncEvent(BOB_VIRTUAL_NODE.holdingIdentity.toAvro(), TokenFullSyncRequest())
        )

        // Then expect nothing to happen, no events to be published
        // and the state to be unchanged
        context.assertOutputRecordCount(TOKEN_CACHE_EVENT, 0)

        context.assertStateOnTopic(
            TOKEN_CACHE_SYNC_EVENT,
            BOB_VIRTUAL_NODE.holdingIdentity,
            TokenSyncMode.FULL_SYNC,
            currentFullSyncState,
            listOf(),
            initialSystemTime,
            0
        )
    }

    @Test
    fun `When full sync event received - periodic check mode - full sync completed recently, ignore and do nothing`() {
        // Given our component and supporting services are running
        val config = mapOf<String, Any>(
            LedgerConfig.UTXO_TOKEN_FULL_SYNC_BLOCK_SIZE to 3,
            LedgerConfig.UTXO_TOKEN_MIN_DELAY_BEFORE_NEXT_FULL_SYNC to 2 //Seconds
        )

        val context = testContextFactory.createTestContext(config)
        context.setSystemTime(initialSystemTime)
        context.startComponent()
        context.assertComponentHasStarted()

        // And Given the sync state for the virtual node is in periodic check mode
        val bobShortId = BOB_VIRTUAL_NODE.holdingIdentity.shortHash.toString()
        val currentFullSyncState = TokenFullSyncState().apply {
            this.startedTimestamp = initialSystemTime
            this.lastBlockCompletedTimestamp = initialSystemTime
            this.blocksCompleted = 0;
            this.recordsCompleted = 0;
            this.nextBlockStartOffset = initialSystemTime
        }

        val currentSyncState = TokenSyncState().apply {
            holdingIdentity = BOB_VIRTUAL_NODE.holdingIdentity.toAvro()
            mode = TokenSyncMode.PERIODIC_CHECK
            fullSyncState = currentFullSyncState
            periodicSyncState = listOf()
            nextWakeup = initialSystemTime
            transientFailureCount = 0
        }

        context.publishState(TOKEN_CACHE_SYNC_EVENT, bobShortId, currentSyncState)

        // When a full sync request event is sent for Bob 99 milliseconds after the last
        // full sync completed
        context.setSystemTime(initialSystemTime.plusMillis(1))

        context.publishEvent(
            TOKEN_CACHE_SYNC_EVENT,
            bobShortId,
            TokenSyncEvent(BOB_VIRTUAL_NODE.holdingIdentity.toAvro(), TokenFullSyncRequest())
        )

        // Then expect nothing to happen, no events to be published
        // and the state to be unchanged
        context.assertOutputRecordCount(TOKEN_CACHE_EVENT, 0)

        context.assertStateOnTopic(
            TOKEN_CACHE_SYNC_EVENT,
            BOB_VIRTUAL_NODE.holdingIdentity,
            TokenSyncMode.PERIODIC_CHECK,
            currentFullSyncState,
            listOf(),
            initialSystemTime,
            0
        )
    }

    @Test
    fun `When full sync event received - periodic check mode - start full sync mode`() {
        // Given our component and supporting services are running
        val config = mapOf<String, Any>(
            LedgerConfig.UTXO_TOKEN_FULL_SYNC_BLOCK_SIZE to 3,
            LedgerConfig.UTXO_TOKEN_MIN_DELAY_BEFORE_NEXT_FULL_SYNC to 2 //Seconds
        )

        val context = testContextFactory.createTestContext(config)
        context.setSystemTime(initialSystemTime)
        context.startComponent()
        context.assertComponentHasStarted()

        // And Given the sync state for the virtual node is in periodic check mode
        val bobShortId = BOB_VIRTUAL_NODE.holdingIdentity.shortHash.toString()
        val currentFullSyncState = TokenFullSyncState().apply {
            this.startedTimestamp = initialSystemTime
            this.lastBlockCompletedTimestamp = initialSystemTime
            this.blocksCompleted = 0;
            this.recordsCompleted = 0;
            this.nextBlockStartOffset = initialSystemTime
        }

        val currentSyncState = TokenSyncState().apply {
            holdingIdentity = BOB_VIRTUAL_NODE.holdingIdentity.toAvro()
            mode = TokenSyncMode.PERIODIC_CHECK
            fullSyncState = currentFullSyncState
            periodicSyncState = listOf()
            nextWakeup = initialSystemTime
            transientFailureCount = 0
        }

        context.publishState(TOKEN_CACHE_SYNC_EVENT, bobShortId, currentSyncState)

        // When a full sync request event is sent for Bob >100 milliseconds after the last
        // full sync completed
        val systemRunTime = initialSystemTime.plusSeconds(3)
        context.setSystemTime(systemRunTime)

        context.publishEvent(
            TOKEN_CACHE_SYNC_EVENT,
            bobShortId,
            TokenSyncEvent(BOB_VIRTUAL_NODE.holdingIdentity.toAvro(), TokenFullSyncRequest())
        )

        // Then we expect to receive the first block of records and the state to be updated
        val expectedFullSyncState = TokenFullSyncState().apply {
            this.startedTimestamp = systemRunTime
            this.lastBlockCompletedTimestamp = systemRunTime
            this.blocksCompleted = 1;
            this.recordsCompleted = 3;
            this.nextBlockStartOffset = initialSystemTime.plusSeconds(2) // timestamp from 3rd record
        }

        val expectedToken1 = token("tx1_bob:0", 100)
        val expectedToken2 = token("tx1_bob:1", 100)
        val expectedToken3 = token("tx2_bob:0", 100)

        val expectedLedgerChangeEvent = TokenLedgerChange(
            bobTokenPoolKey,
            listOf(),
            listOf(expectedToken1, expectedToken2, expectedToken3)
        )

        context.assertStateOnTopic(
            TOKEN_CACHE_SYNC_EVENT,
            BOB_VIRTUAL_NODE.holdingIdentity,
            TokenSyncMode.FULL_SYNC,
            expectedFullSyncState,
            listOf(),
            systemRunTime,
            0
        )

        context.assertExpectedLedgerChangeEventRecords(
            TOKEN_CACHE_EVENT,
            listOf(expectedLedgerChangeEvent)
        )
    }

    @Test
    fun `When wake-up event received - full sync mode - send next block of full sync records from the DB`() {
    }

    @Test
    fun `When wake-up event received - full sync mode - and all records read, then switch back to periodic check mode`() {
    }
}