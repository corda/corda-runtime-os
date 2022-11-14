package net.corda.utxo.token.sync.integration.tests.context

import com.typesafe.config.ConfigFactory
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenCachedSyncCheck
import net.corda.data.ledger.utxo.token.selection.data.TokenFullSyncState
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.data.TokenPoolPeriodicSyncState
import net.corda.data.ledger.utxo.token.selection.data.TokenSyncWakeUp
import net.corda.data.ledger.utxo.token.selection.data.TokenUnspentSyncCheck
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.event.TokenSyncEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncMode
import net.corda.data.ledger.utxo.token.selection.state.TokenSyncState
import net.corda.db.testkit.TestDbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.LedgerConfig
import net.corda.utilities.time.Clock
import net.corda.utxo.token.sync.TokenCacheSyncServiceComponent
import net.corda.utxo.token.sync.factories.impl.TokenCacheSyncComponentFactoryImpl
import net.corda.utxo.token.sync.integration.tests.fakes.PublisherFactoryFake
import net.corda.utxo.token.sync.integration.tests.fakes.StateAndEventSubscriptionFake
import net.corda.utxo.token.sync.integration.tests.fakes.SubscriptionFactoryFake
import net.corda.utxo.token.sync.integration.tests.fakes.TestConfigurationReadService
import net.corda.v5.base.util.uncheckedCast
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.fake.VirtualNodeInfoReadServiceFake
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Fail
import org.junit.jupiter.api.Assertions
import java.time.Instant
import kotlin.math.exp

class TestContext(
    vNodes: List<VirtualNodeInfo>,
    dbConnectionManager: TestDbConnectionManager,
    overrideConfig: Map<String, Any>
) {

    private val component: TokenCacheSyncServiceComponent
    private val recordsReceived = mutableListOf<Record<*, *>>()
    private val lifecycleCoordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
    private val subscriptionFactory = SubscriptionFactoryFake(lifecycleCoordinatorFactory)
    private val configReadService = TestConfigurationReadService(lifecycleCoordinatorFactory)
    private val virtualNodeInfoReadService = VirtualNodeInfoReadServiceFake(lifecycleCoordinatorFactory)
    private val clock = ClockFake()

    init {
        val emptyConfig: SmartConfig =
            SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())

        val testConfig = mutableMapOf<String, Any>(
            LedgerConfig.UTXO_TOKEN_FULL_SYNC_BLOCK_SIZE to 1,
            LedgerConfig.UTXO_TOKEN_MIN_DELAY_BEFORE_NEXT_FULL_SYNC to 1,
            LedgerConfig.UTXO_TOKEN_MIN_DELAY_BEFORE_NEXT_PERIODIC_SYNC to 1,
            LedgerConfig.UTXO_TOKEN_PERIODIC_CHECK_BLOCK_SIZE to 1,
            LedgerConfig.UTXO_TOKEN_SEND_WAKEUP_MAX_RETRY_ATTEMPTS to 1,
            LedgerConfig.UTXO_TOKEN_SEND_WAKEUP_MAX_RETRY_DELAY to 1,
        )

        overrideConfig.forEach { testConfig[it.key] = it.value }

        val cfg = ConfigFactory.parseMap(testConfig)

        val syncConfig =
            SmartConfigFactory.create(cfg)
                .create(cfg)

        val config = listOf(
            ConfigKeys.BOOT_CONFIG to emptyConfig,
            ConfigKeys.MESSAGING_CONFIG to emptyConfig,
            ConfigKeys.UTXO_LEDGER_CONFIG to syncConfig
        )

        configReadService.configUpdates.clear()
        configReadService.configUpdates.addAll(config)

        val publisherFactory = PublisherFactoryFake(::onRecordsReceived)

        vNodes.forEach { virtualNodeInfoReadService.addOrUpdate(it) }

        component = TokenCacheSyncComponentFactoryImpl(
            lifecycleCoordinatorFactory,
            configReadService,
            virtualNodeInfoReadService,
            dbConnectionManager,
            subscriptionFactory,
            publisherFactory,
            clock
        ).create()
    }

    fun setSystemTime(now: Instant) {
        clock.now = now
    }

    fun startComponent() {
        virtualNodeInfoReadService.start()
        configReadService.start()
        component.start()
    }

    fun assertComponentHasStarted() {
        assertThat(
            lifecycleCoordinatorFactory
                .registry
                .getCoordinator(LifecycleCoordinatorName.forComponent<TokenCacheSyncServiceComponent>())
                .status
        ).isEqualTo(LifecycleStatus.UP)
    }

    fun assertTokenSyncWakUpReceived(topic: String, holdingIdentity: HoldingIdentity) {
        val record = recordsReceived.firstOrNull {
            it.topic == topic && it.key == holdingIdentity.shortHash.toString()
        }

        assertThat(record)
            .withFailMessage(
                " failed to find record for topic '${topic}' and key '${holdingIdentity.shortHash}'"
            )
            .isNotNull

        val expectedPayload = TokenSyncEvent().apply {
            this.holdingIdentity = holdingIdentity.toAvro()
            this.payload = TokenSyncWakeUp()
        }

        assertThat(record!!.value).isEqualTo(expectedPayload)
    }

    private fun onRecordsReceived(records: List<Record<*, *>>) {
        recordsReceived.addAll(records)
    }

    @Suppress("UNCHECKED_CAST")
    fun publishEvent(
        topic: String,
        key: String,
        payload: TokenSyncEvent
    ) {
        (subscriptionFactory.stateAndEventSubscriptions[topic] as StateAndEventSubscriptionFake<Any, Any, Any>)
            .publish(key, Record(topic, key, payload))
    }

    @Suppress("UNCHECKED_CAST")
    fun publishState(
        topic: String,
        key: String,
        state: TokenSyncState
    ) {
        (subscriptionFactory.stateAndEventSubscriptions[topic] as StateAndEventSubscriptionFake<Any, Any, Any>)
            .publishState(key, state)
    }

    fun assertExpectedLedgerChangeEventRecords(topic: String, expectedEvents: List<TokenLedgerChange>) {
        val expectedRecords = expectedEvents.map { Record(topic, it.poolKey, TokenPoolCacheEvent(it.poolKey, it)) }
        assertThat(getPublishedRecordsFor(topic)).containsOnly(*expectedRecords.toTypedArray())
    }

    fun assertTokenUnspentSyncCheckEventRecords(topic: String, expectedEvents: List<TokenCachedSyncCheck>) {
        val expectedRecords = expectedEvents.map { Record(topic, it.poolKey, TokenPoolCacheEvent(it.poolKey, it)) }
        val actualRecords = getPublishedRecordsFor(topic)
        assertThat(actualRecords).containsOnly(*expectedRecords.toTypedArray())
    }

    fun assertTokenUnspentSyncCheckRecord(
        topic: String,
        tokenPoolCacheKey: TokenPoolCacheKey,
        expectedStateRefs: List<String>
    ) {
        val match = getPublishedRecordsFor(topic).firstOrNull {
            matchPoolKey(it.key as TokenPoolCacheKey, tokenPoolCacheKey) &&
                    matchPoolKey((it.value as TokenPoolCacheEvent).poolKey, tokenPoolCacheKey) &&
                    matchTokenUnspentSyncCheck(
                        (it.value as TokenPoolCacheEvent).payload as TokenUnspentSyncCheck,
                        expectedStateRefs
                    )
        }
        assertThat(match)
            .withFailMessage {
                "Failed to find expected record topic='${topic}' key='${tokenPoolCacheKey}' expectedRefs='${expectedStateRefs} \n in \n'${
                    getPublishedRecordsFor(
                        topic
                    )
                }'"
            }
            .isNotNull
    }

    fun clearPublishedRecords(topic: String) {
        getPublishedRecordsFor(topic).clear()
    }

    fun assertOutputRecordCount(topic: String, count: Int) {
        assertThat(getPublishedRecordsFor(topic)).hasSize(count)
    }

    fun assertStateOnTopic(
        expectedTopic: String,
        expectedHoldingIdentity: HoldingIdentity,
        expectedMode: TokenSyncMode,
        expectedFullSyncState: TokenFullSyncState?,
        expectedPeriodicState: List<TokenPoolPeriodicSyncState>,
        expectedNextWakeup: Instant,
        expectedTransientFailureCount: Int
    ) {
        val subscription = getSubscriptionFor(expectedTopic)

        assertThat(subscription.getState()[expectedHoldingIdentity.shortHash.toString()])
            .withFailMessage { "Could not find state for virtual node '$expectedHoldingIdentity'" }
            .isNotNull

        val state = subscription.getState()[expectedHoldingIdentity.shortHash.toString()]!!

        assertThat(state.mode).isEqualTo(expectedMode)

        assertFullSyncState(state.fullSyncState, expectedFullSyncState)

        assertPeriodicSyncState(state.periodcSyncstate, expectedPeriodicState)

        assertThat(state.nextWakeup)
            .isEqualTo(expectedNextWakeup)

        assertThat(state.transientFailureCount)
            .isEqualTo(expectedTransientFailureCount)
    }

    private fun assertFullSyncState(fullSyncState: TokenFullSyncState?, expected: TokenFullSyncState?) {
        if (fullSyncState == null && expected == null) {
            return
        }

        assertThat(fullSyncState).isNotNull
        assertThat(expected).isNotNull

        assertThat(fullSyncState!!.startedTimestamp).isEqualTo(expected!!.startedTimestamp)
        assertThat(fullSyncState.lastBlockCompletedTimestamp).isEqualTo(expected.lastBlockCompletedTimestamp)
        assertThat(fullSyncState.blocksCompleted).isEqualTo(expected.blocksCompleted)
        assertThat(fullSyncState.nextBlockStartOffset).isEqualTo(expected.nextBlockStartOffset)
    }

    private fun assertPeriodicSyncState(
        actualStates: List<TokenPoolPeriodicSyncState>,
        expectedStates: List<TokenPoolPeriodicSyncState>
    ) {
        assertThat(actualStates).hasSize(expectedStates.size)

        expectedStates.forEach { expected ->
            val match = actualStates.firstOrNull { actual ->
                matchPoolKey(actual.poolKey, expected.poolKey) &&
                        actual.nextBlockStartOffset == expected.nextBlockStartOffset
            }

            assertThat(match)
                .withFailMessage { "Failed to find expected periodic sync state '${expected}' in '${actualStates}'" }
                .isNotNull
        }
    }

    private fun matchPoolKey(lhs: TokenPoolCacheKey, rhs: TokenPoolCacheKey): Boolean {
        return lhs.shortHolderId == rhs.shortHolderId &&
                lhs.tokenType == rhs.tokenType &&
                lhs.issuerHash == rhs.issuerHash &&
                lhs.notaryX500Name == rhs.notaryX500Name &&
                lhs.symbol == rhs.symbol
    }

    private fun matchTokenUnspentSyncCheck(payload: TokenUnspentSyncCheck, expectedStateRefs: List<String>): Boolean {
        return expectedStateRefs.size == payload.tokenRefs.size &&
                payload.tokenRefs.all { expectedStateRefs.contains(it) }
    }

    private fun matchToken(expectedToken: Token, actualTokens: List<Token>): Boolean {
        return actualTokens.any {
            it.stateRef == expectedToken.stateRef &&
                    it.amount.scale == expectedToken.amount.scale &&
                    it.stateRef == expectedToken.stateRef
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getSubscriptionFor(topic: String)
            : StateAndEventSubscriptionFake<String, TokenSyncState, TokenSyncEvent> {
        return (
                subscriptionFactory.stateAndEventSubscriptions[topic]
                        as StateAndEventSubscriptionFake<String, TokenSyncState, TokenSyncEvent>
                )
    }

    private fun getPublishedRecordsFor(topic: String): MutableList<Record<*, *>> {
        return subscriptionFactory.publishedRecords[topic]?:mutableListOf()
    }

    private class ClockFake : Clock {
        var now: Instant = Instant.EPOCH
        override fun instant(): Instant {
            return now
        }
    }
}