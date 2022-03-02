package net.corda.p2p.linkmanager.delivery

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.AutoClosableScheduledExecutorService
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
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
import java.time.Instant
import java.util.*
import java.util.concurrent.*

class ReplaySchedulerTest {

    companion object {
        private const val REPLAY_PERIOD_KEY = "REPLAY PERIOD"
        private const val MAX_REPLAYING_MESSAGES = 100
        private val replayPeriod = Duration.ofMillis(2)
        private val sessionCounterparties = SessionManager.SessionCounterparties(
            LinkManagerNetworkMap.HoldingIdentity("Source", DeliveryTrackerTest.groupId),
            LinkManagerNetworkMap.HoldingIdentity("Dest", DeliveryTrackerTest.groupId)
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

    private var now = Instant.ofEpochSecond(0)

    private fun createFuture(timeAndTask: Pair<Instant, Runnable>): ScheduledFuture<*> {
        return mock {
            on { cancel(any()) } doAnswer {
                scheduledTasks.remove(timeAndTask)
            }
        }
    }

    private val scheduledTasks: MutableList<Pair<Instant, Runnable>> = mutableListOf()
    private val mockScheduledExecutor = mock<ScheduledExecutorService> {
        on { schedule(any(), any(), any()) } doAnswer {
            @Suppress("UNCHECKED_CAST")
            val task = it.arguments[0] as Runnable
            val delay = it.arguments[1] as Long
            val timeUnit = it.arguments[2] as TimeUnit
            val timeToExecute = now.plus(delay, timeUnit.toChronoUnit())
            val timeAndTask = timeToExecute to task
            scheduledTasks.add(timeAndTask)

            createFuture(timeAndTask)
        }
    }

    @AfterEach
    fun cleanUp() {
        loggingInterceptor.reset()
        dominoTile.close()
        resourcesHolder.close()
        configResourcesHolder.close()
    }

    @Test
    fun `The ReplayScheduler will not replay before start`() {
        val replayManager = ReplayScheduler(coordinatorFactory, service, false, REPLAY_PERIOD_KEY, { _: Any -> }) { 0 }
        assertThrows<IllegalStateException> {
            replayManager.addForReplay(0,"", Any(), Mockito.mock(SessionManager.SessionCounterparties::class.java))
        }
    }

    @Test
    fun `on applyNewConfiguration completes the future exceptionally if config is invalid`() {
        ReplayScheduler(coordinatorFactory, service, false, REPLAY_PERIOD_KEY, { _: Any -> }) { 0 }
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
        ReplayScheduler(coordinatorFactory, service, false, REPLAY_PERIOD_KEY, { _: Any -> }) { 0 }
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
        ReplayScheduler(coordinatorFactory, service, false, REPLAY_PERIOD_KEY, { _: Any -> }, {mockScheduledExecutor}) { 0 }
        val future = createResources(resourcesHolder)
        verify(resourcesHolder).keep(isA<AutoClosableScheduledExecutorService>())
        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `The ReplayScheduler replays added messages repeatidly`() {
        val messages = 9

        val tracker = TrackReplayedMessages()
        val replayManager = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            REPLAY_PERIOD_KEY,
            tracker::replayMessage,
            {mockScheduledExecutor}
        ) { 0 }
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
            advanceTime(replayPeriod)
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
            {mockScheduledExecutor}
        ) { 0 }
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
        advanceTime(replayPeriod)
        assertThat(tracker.messages).containsExactlyInAnyOrderElementsOf(messageIds)
        tracker.messages.clear()
        replayManager.removeAllMessagesFromReplay()
        advanceTime(replayPeriod)
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
            {mockScheduledExecutor}
        ) { 0 }
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, MAX_REPLAYING_MESSAGES),
            null,
            configResourcesHolder
        )

        val messageIdsToRemove = mutableListOf<String>()
        val messageIdsToNotRemove = mutableListOf<String>()
        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            if (i % 2 == 0) messageIdsToRemove.add(messageId)
            else messageIdsToNotRemove.add(messageId)
            replayManager.addForReplay(
                0,
                messageId,
                messageId,
                sessionCounterparties
            )
        }

        //Acknowledge all even messages
        for (id in messageIdsToRemove) {
            replayManager.removeFromReplay(id, sessionCounterparties)
        }

        advanceTime(replayPeriod)
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
            {mockScheduledExecutor}
        ) { 0 }
        replayManager.start()
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, MAX_REPLAYING_MESSAGES),
             null,
            configResourcesHolder
        )

        replayManager.addForReplay(0, "", message, sessionCounterparties)
        advanceTime(replayPeriod)
        loggingInterceptor.assertErrorContains(
            "An exception was thrown when replaying a message. The task will be retried again in ${replayPeriod.toMillis()} ms.")
        assertThat(tracker.messages).isEmpty()

        advanceTime(replayPeriod)
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
            {mockScheduledExecutor}
        ) { 0 }
        replayManager.start()
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(mock(), null, configResourcesHolder)

        val messageId = UUID.randomUUID().toString()
        replayManager.addForReplay(0, messageId, messageId, sessionCounterparties)
        advanceTime(replayPeriod)
        tracker.messages.clear()
        configHandler.applyNewConfiguration(mock(), null, configResourcesHolder)

        val messageIdAfterUpdate = UUID.randomUUID().toString()
        replayManager.addForReplay(
            0,
            messageIdAfterUpdate,
            messageIdAfterUpdate,
            sessionCounterparties
        )
        advanceTime(replayPeriod)

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
            {mockScheduledExecutor}
        ) { 0 }
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

        advanceTime(replayPeriod)
        replayManager.removeFromReplay(messageId, sessionCounterparties)
        advanceTime(replayPeriod)

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
            {mockScheduledExecutor}
        ) { 0 }
        replayManager.start()
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, messageCap),
            null,
            configResourcesHolder
        )
        val addedMessages = mutableListOf<String>()
        for (i in 0 until 2 * messageCap) {
            val messageId = UUID.randomUUID().toString()
            replayManager.addForReplay(0, messageId, messageId, sessionCounterparties)
            addedMessages.add(messageId)
        }
        advanceTime(replayPeriod)
        tracker.messages.clear()

        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, 2 * messageCap),
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod, messageCap),
            configResourcesHolder
        )

        advanceTime(replayPeriod)
        assertThat(tracker.messages).containsExactlyInAnyOrderElementsOf(addedMessages)
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

    private fun advanceTime(duration: Duration) {
        now = now.plusMillis(duration.toMillis())
        val iterator = scheduledTasks.iterator()
        val tasksToExecute = mutableListOf<Runnable>()
        while (iterator.hasNext()) {
            val (time, task) = iterator.next()
            if (time.isBefore(now) || time == now) {
                tasksToExecute.add(task)
                iterator.remove()
            }
        }

        // execute them outside the loop to avoid concurrent modification (when the tasks schedule tasks themselves)
        tasksToExecute.forEach { it.run() }
    }
}
