package net.corda.p2p.linkmanager.delivery

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.linkmanager.LinkManagerInternalTypes
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.AutoClosableScheduledExecutorService
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.test.util.MockTimeFacilitiesProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.*
import java.util.concurrent.*

class ReplaySchedulerTest {

    companion object {
        private const val REPLAY_PERIOD_KEY = "REPLAY PERIOD"
        private const val MAX_REPLAYING_MESSAGES = 100
        private val replayPeriod = Duration.ofMillis(2)
        private val sessionCounterparties = SessionManager.SessionCounterparties(
            LinkManagerInternalTypes.HoldingIdentity("Source", DeliveryTrackerTest.groupId),
            LinkManagerInternalTypes.HoldingIdentity("Dest", DeliveryTrackerTest.groupId)
        )
        lateinit var loggingInterceptor: LoggingInterceptor

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> ()
    private val service = mock<ConfigurationReadService>()
    private val resourcesHolder = mock<ResourcesHolder>()
    private val configResourcesHolder = mock<ResourcesHolder>()

    private lateinit var configHandler: ReplayScheduler<*>.ReplaySchedulerConfigurationChangeHandler
    private lateinit var createResources: ((resources: ResourcesHolder) -> CompletableFuture<Unit>)
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        @Suppress("UNCHECKED_CAST")
        createResources = context.arguments()[2] as ((ResourcesHolder) -> CompletableFuture<Unit>)
        configHandler = context.arguments()[5] as ReplayScheduler<*>.ReplaySchedulerConfigurationChangeHandler
    }
    private val mockTimeFacilitiesProvider = MockTimeFacilitiesProvider()


    @AfterEach
    fun cleanUp() {
        loggingInterceptor.reset()
        dominoTile.close()
        resourcesHolder.close()
        configResourcesHolder.close()
    }

    @Test
    fun `The ReplayScheduler will not replay before start`() {
        val replayManager = ReplayScheduler(coordinatorFactory, service, false, REPLAY_PERIOD_KEY,
            { _: Any -> }, clock = mockTimeFacilitiesProvider.mockClock)
        assertThrows<IllegalStateException> {
            replayManager.addForReplay(0,"", Any(), Mockito.mock(SessionManager.SessionCounterparties::class.java))
        }
    }

    @Test
    fun `on applyNewConfiguration completes the future exceptionally if config is invalid`() {
        ReplayScheduler(coordinatorFactory, service, false, REPLAY_PERIOD_KEY, { _: Any -> }, clock = mockTimeFacilitiesProvider.mockClock)
        val future = configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(Duration.ofMillis(-10), Duration.ofMillis(-10), MAX_REPLAYING_MESSAGES),
            null,
            configResourcesHolder
        )

        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isTrue
    }

    @Test
    fun `on applyNewConfiguration completes the future if config is valid`() {
        ReplayScheduler(coordinatorFactory, service, false, REPLAY_PERIOD_KEY, { _: Any -> }, clock = mockTimeFacilitiesProvider.mockClock)
        val future = configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, MAX_REPLAYING_MESSAGES),
            null,
            configResourcesHolder
        )

        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `on createResource the ReplayScheduler adds a executor service to the resource holder`() {
        ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            REPLAY_PERIOD_KEY,
            { _: Any -> },
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.mockClock)
        val future = createResources(resourcesHolder)
        verify(resourcesHolder).keep(isA<AutoClosableScheduledExecutorService>())
        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `The ReplayScheduler replays added messages repeatedly`() {
        val messages = 9

        val tracker = TrackReplayedMessages()
        val replayManager = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            REPLAY_PERIOD_KEY,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.mockClock)
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, MAX_REPLAYING_MESSAGES),
            null,
            configResourcesHolder
        )

        val messageIds =  mutableListOf<String>()
        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            replayManager.addForReplay(
                0,
                messageId,
                messageId,
                sessionCounterparties
            )
            messageIds.add(messageId)
        }

        val repeats = 3
        for (i in 0 until repeats) {
            mockTimeFacilitiesProvider.advanceTime(replayPeriod)
            assertThat(tracker.messages).containsExactlyInAnyOrderElementsOf(messageIds)
            tracker.messages.clear()
        }

        replayManager.stop()
    }

    @Test
    fun `The ReplayScheduler stops replaying messages after removeAllMessages`() {
        val messages = 9

        val tracker = TrackReplayedMessages()
        val replayManager = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            REPLAY_PERIOD_KEY,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.mockClock)
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, MAX_REPLAYING_MESSAGES),
            null,
            configResourcesHolder
        )

        val messageIds = (1..messages).map { UUID.randomUUID().toString() }
        messageIds.forEach { replayManager.addForReplay(0, it, it, sessionCounterparties) }
        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        assertThat(tracker.messages).containsExactlyInAnyOrderElementsOf(messageIds)
        tracker.messages.clear()
        replayManager.removeAllMessagesFromReplay()
        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        assertThat(tracker.messages).isEmpty()
    }

    @Test
    fun `The ReplayScheduler doesn't replay removed messages`() {
        val messages = 8

        val tracker = TrackReplayedMessages()
        val replayManager = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            REPLAY_PERIOD_KEY,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.mockClock)
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, MAX_REPLAYING_MESSAGES),
            null,
            configResourcesHolder
        )

        val messageIdsToRemove = (1..messages).map { UUID.randomUUID().toString() }
        val messageIdsToNotRemove = (1..messages).map { UUID.randomUUID().toString() }
        messageIdsToRemove.forEach { replayManager.addForReplay(0, it, it, sessionCounterparties) }
        messageIdsToNotRemove.forEach { replayManager.addForReplay(0, it, it, sessionCounterparties) }

        //Acknowledge all even messages
        messageIdsToRemove.forEach {
            replayManager.removeFromReplay(it, sessionCounterparties)
        }

        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        assertThat(tracker.messages).containsExactlyInAnyOrderElementsOf(messageIdsToNotRemove)
    }

    @Test
    fun `The ReplayScheduler handles exceptions`() {
        val message = "message"
        val tracker = TrackReplayedMessages()
        var firstCall = true
        fun replayMessage(message: String) {
            if (firstCall) {
                firstCall = false
                throw MyException()
            } else {
                tracker.replayMessage(message)
            }
        }

        val replayManager = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            REPLAY_PERIOD_KEY,
            ::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.mockClock)
        replayManager.start()
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, MAX_REPLAYING_MESSAGES),
             null,
            configResourcesHolder
        )

        replayManager.addForReplay(0, "", message, sessionCounterparties)
        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        loggingInterceptor.assertErrorContains(
            "An exception was thrown when replaying a message. The task will be retried again in ${replayPeriod.toMillis()} ms.")
        assertThat(tracker.messages).isEmpty()

        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        assertThat(tracker.messages).containsOnly(message)
    }

    @Test
    fun `The ReplayScheduler replays added messages after config update`() {
        val tracker = TrackReplayedMessages()
        val replayManager = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            REPLAY_PERIOD_KEY,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.mockClock)
        replayManager.start()
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(mock(), null, configResourcesHolder)

        val messageId = UUID.randomUUID().toString()
        replayManager.addForReplay(0, messageId, messageId, sessionCounterparties)
        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        tracker.messages.clear()
        configHandler.applyNewConfiguration(mock(), null, configResourcesHolder)

        val messageIdAfterUpdate = UUID.randomUUID().toString()
        replayManager.addForReplay(
            0,
            messageIdAfterUpdate,
            messageIdAfterUpdate,
            sessionCounterparties
        )
        mockTimeFacilitiesProvider.advanceTime(replayPeriod)

        assertThat(tracker.messages).contains(messageId, messageIdAfterUpdate)
    }

    @Test
    fun `queued messages which are removed are not replayed`() {
        val messageCap = 1
        val tracker = TrackReplayedMessages()
        val replayManager = ReplayScheduler(
            coordinatorFactory,
            service,
            true,
            REPLAY_PERIOD_KEY,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.mockClock)
        replayManager.start()
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, messageCap),
            null,
            configResourcesHolder
        )

        val messageId = UUID.randomUUID().toString()
        replayManager.addForReplay(0, messageId, messageId, sessionCounterparties)

        val queuedMessageId = UUID.randomUUID().toString()
        replayManager.addForReplay(0, queuedMessageId, queuedMessageId, sessionCounterparties)
        replayManager.removeFromReplay(queuedMessageId, sessionCounterparties)

        val anotherQueuedMessageId = UUID.randomUUID().toString()
        replayManager.addForReplay(0, anotherQueuedMessageId, anotherQueuedMessageId, sessionCounterparties)

        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        replayManager.removeFromReplay(messageId, sessionCounterparties)
        mockTimeFacilitiesProvider.advanceTime(replayPeriod)

        assertThat(tracker.messages).containsOnly(messageId, anotherQueuedMessageId)
    }

    @Test
    fun `If the ReplayScheduler cap increases queued messages are replayed`() {
        val messageCap = 3
        val tracker = TrackReplayedMessages()
        val replayManager = ReplayScheduler(
            coordinatorFactory,
            service,
            true,
            REPLAY_PERIOD_KEY,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.mockClock)
        replayManager.start()
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, messageCap),
            null,
            configResourcesHolder
        )

        val messageIds = (1..2 * messageCap).map { UUID.randomUUID().toString() }
        messageIds.forEach { replayManager.addForReplay(0, it, it, sessionCounterparties) }

        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        tracker.messages.clear()

        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, 2 * messageCap),
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, messageCap),
            configResourcesHolder
        )

        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        assertThat(tracker.messages).containsExactlyInAnyOrderElementsOf(messageIds)
    }

    class MyException: Exception("Ohh No")

    class TrackReplayedMessages {
        val messages = mutableListOf<String>()

        fun replayMessage(message: String) {
            messages.add(message)
        }
    }

    private fun setRunning() {
        whenever(dominoTile.constructed().first().isRunning).doReturn(true)
    }
}
