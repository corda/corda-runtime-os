package net.corda.p2p.linkmanager.sessions.expiration

import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.schema.Schemas.ScheduledTask.SCHEDULED_TASK_NAME_STALE_P2P_SESSION_CLEANUP
import net.corda.schema.Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_STALE_P2P_SESSION_PROCESSOR
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class StaleSessionProcessorTest {
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val subscription = mock<Subscription<String, ScheduledTaskTrigger>>()
    private val configuration = mock<SmartConfig>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createDurableSubscription(
                any(),
                any<DurableProcessor<String, ScheduledTaskTrigger>>(),
                eq(configuration),
                anyOrNull(),
            )
        } doReturn subscription
    }
    private val subscriptionTile = Mockito.mockConstruction(SubscriptionDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        (context.arguments()[1] as (() -> Subscription<String, ScheduledTaskTrigger>)).invoke()
    }
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java)
    private val clock = mock<Clock> {
        on { instant() } doReturn Instant.ofEpochMilli(1000)
    }
    private val firstState = mock<State>()
    private val secondState = mock<State>()
    private val expiredStates = mapOf(
        "key1" to firstState,
        "key2" to secondState,
    )
    private val expectedMetadataFilter = listOf(
        MetadataFilter("expiry", Operation.LesserThan, clock.instant().toEpochMilli())
    )
    private val stateManager = mock<StateManager> {
        on { findByMetadataMatchingAny(eq(expectedMetadataFilter)) } doReturn expiredStates
    }
    private val sessionCache = mock<SessionCache>()
    private val commonComponents = mock<CommonComponents> {
        on { lifecycleCoordinatorFactory } doReturn lifecycleCoordinatorFactory
        on { subscriptionFactory } doReturn subscriptionFactory
        on { clock } doReturn clock
        on { messagingConfiguration } doReturn configuration
        on { stateManager } doReturn stateManager
    }
    private val staleSessionProcessor = StaleSessionProcessor(
        commonComponents, sessionCache
    )

    @AfterEach
    fun cleanUp() {
        subscriptionTile.close()
        dominoTile.close()
    }

    @Test
    fun `delete is called twice if there were expired states`() {
        val result = staleSessionProcessor.onNext(
            listOf(
                Record(
                    topic = SCHEDULED_TASK_TOPIC_STALE_P2P_SESSION_PROCESSOR,
                    key = SCHEDULED_TASK_NAME_STALE_P2P_SESSION_CLEANUP,
                    value = ScheduledTaskTrigger(),
                )
            )
        )
        verify(sessionCache, times(expiredStates.size)).forgetState(any())
        verify(sessionCache).forgetState(eq(firstState))
        verify(sessionCache).forgetState(eq(secondState))
        assertThat(result).isEmpty()
    }

    @Test
    fun `nothing happens if there were no session clean up events`() {
        val result = staleSessionProcessor.onNext(emptyList())
        verify(stateManager, never()).findByMetadataMatchingAny(eq(expectedMetadataFilter))
        verify(sessionCache, never()).forgetState(any())
        assertThat(result).isEmpty()
    }

    @Test
    fun `delete is not called if there were no expired states`() {
        whenever(stateManager.findByMetadataMatchingAny(eq(expectedMetadataFilter))).thenReturn(emptyMap())
        val result = staleSessionProcessor.onNext(
            listOf(
                Record(
                    topic = SCHEDULED_TASK_TOPIC_STALE_P2P_SESSION_PROCESSOR,
                    key = SCHEDULED_TASK_NAME_STALE_P2P_SESSION_CLEANUP,
                    value = ScheduledTaskTrigger(),
                )
            )
        )
        verify(sessionCache, never()).forgetState(any())
        assertThat(result).isEmpty()
    }

    @Test
    fun `delete is not called if the query to get the expired states fails`() {
        whenever(stateManager.findByMetadataMatchingAny(eq(expectedMetadataFilter)))
            .thenThrow(CordaRuntimeException("exception test"))
        val result = staleSessionProcessor.onNext(
            listOf(
                Record(
                    topic = SCHEDULED_TASK_TOPIC_STALE_P2P_SESSION_PROCESSOR,
                    key = SCHEDULED_TASK_NAME_STALE_P2P_SESSION_CLEANUP,
                    value = ScheduledTaskTrigger(),
                )
            )
        )
        verify(sessionCache, never()).forgetState(any())
        assertThat(result).isEmpty()
    }
}