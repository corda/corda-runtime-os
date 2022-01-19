package net.corda.p2p.linkmanager.delivery

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.AutoClosableScheduledExecutorService
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

class ReplaySchedulerTest {

    companion object {
        private const val REPLAY_PERIOD_KEY = "REPLAY PERIOD"
        private val replayPeriod = Duration.ofMillis(2)
        private val sessionKey = SessionManager.SessionKey(
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
    private val dominoTile = Mockito.mockConstruction(DominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        @Suppress("UNCHECKED_CAST")
        createResources = context.arguments()[2] as ((ResourcesHolder) -> CompletableFuture<Unit>)
        configHandler = context.arguments()[4] as ReplayScheduler<*>.ReplaySchedulerConfigurationChangeHandler
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
        val replayManager = ReplayScheduler(coordinatorFactory, service, REPLAY_PERIOD_KEY, { _: Any -> }) { 0 }
        assertThrows<IllegalStateException> {
            replayManager.addForReplay(0,"", Any(), Mockito.mock(SessionManager.SessionKey::class.java))
        }
    }

    @Test
    fun `on applyNewConfiguration completes the future exceptionally if config is invalid`() {
        ReplayScheduler(coordinatorFactory, service, REPLAY_PERIOD_KEY, { _: Any -> }) { 0 }
        val future = configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(Duration.ofMillis(-10), Duration.ofMillis(-10)),
            null,
            configResourcesHolder
        )

        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isTrue
    }

    @Test
    fun `on applyNewConfiguration completes the future if config is valid`() {
        ReplayScheduler(coordinatorFactory, service, REPLAY_PERIOD_KEY, { _: Any -> }) { 0 }
        val future = configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod),
            null,
            configResourcesHolder
        )

        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `on createResource the ReplayScheduler adds a executor service to the resource holder`() {
        ReplayScheduler(coordinatorFactory, service, REPLAY_PERIOD_KEY, { _: Any -> }) { 0 }
        val future = createResources(resourcesHolder)
        verify(resourcesHolder).keep(isA<AutoClosableScheduledExecutorService>())
        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `The ReplayScheduler replays added messages`() {
        val messages = 9

        val tracker = TrackReplayedMessages(messages)
        val replayManager = ReplayScheduler(coordinatorFactory, service, REPLAY_PERIOD_KEY, tracker::replayMessage) { 0 }
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod),
            null,
            configResourcesHolder
        )

        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            replayManager.addForReplay(
                0,
                messageId,
                messageId,
                sessionKey
            )
        }

        tracker.await()
        replayManager.stop()
    }

    @Test
    fun `The ReplayScheduler stops replaying messages after removeAllMessages`() {
        val messages = 9

        val tracker = TrackReplayedMessages(messages)
        val replayManager = ReplayScheduler(coordinatorFactory, service, REPLAY_PERIOD_KEY, tracker::replayMessage) { 0 }
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod),
            null,
            configResourcesHolder
        )

        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            replayManager.addForReplay(
                0,
                messageId,
                messageId,
                sessionKey
            )
        }
        tracker.await()
        replayManager.removeAllMessagesFromReplay()
        val totalMessagesAfterRemoveAll = tracker.numberOfReplays
        Thread.sleep(5 * replayPeriod.toMillis())

        assertThat(tracker.numberOfReplays).isEqualTo(totalMessagesAfterRemoveAll)
        replayManager.stop()
    }

    @Test
    fun `The ReplayScheduler doesn't replay removed messages`() {
        val messages = 8

        val tracker = TrackReplayedMessages(messages)
        val replayManager = ReplayScheduler(coordinatorFactory, service, REPLAY_PERIOD_KEY, tracker::replayMessage) { 0 }
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod),
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
                sessionKey
            )
        }

        tracker.await()
        //Acknowledge all even messages
        for (id in messageIdsToRemove) {
            replayManager.removeFromReplay(id, sessionKey)
        }

        //Wait some time to until the even messages should have stopped replaying
        Thread.sleep(2 * messages * replayPeriod.toMillis())
        val removedMessages = mutableMapOf<String, Int>()
        for (id in messageIdsToRemove) {
            removedMessages[id] = tracker.numberOfReplays[id]!!
        }

        //Wait again and check the number of replays for each stopped message is the same
        Thread.sleep(2 * messages * replayPeriod.toMillis())
        for (id in messageIdsToRemove) {
            assertEquals(removedMessages[id], tracker.numberOfReplays[id]!!)
        }
    }

    @Test
    fun `The ReplayScheduler handles exceptions`() {
        val message = "message"
        val tracker = TrackReplayedMessages(2, 1)
        val replayManager = ReplayScheduler(coordinatorFactory, service, REPLAY_PERIOD_KEY, tracker::replayMessage) { 0 }
        replayManager.start()
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod),
             null,
            configResourcesHolder
        )

        replayManager.addForReplay(0, "", message, sessionKey)
        tracker.await()
        loggingInterceptor.assertErrorContains(
            "An exception was thrown when replaying a message. The task will be retried again in ${replayPeriod.toMillis()} ms.")
        replayManager.stop()
        assertTrue(tracker.numberOfReplays[message]!! >= 1)
    }

    @Test
    fun `The ReplayScheduler replays added messages after config update`() {
        val tracker = TrackReplayedMessages( 2)
        val replayManager = ReplayScheduler(coordinatorFactory, service, REPLAY_PERIOD_KEY, tracker::replayMessage) { 0 }
        replayManager.start()
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod, replayPeriod),
             null,
            configResourcesHolder
        )

        val messageId = UUID.randomUUID().toString()
        replayManager.addForReplay(
           0,
            messageId,
            messageId,
            sessionKey
        )

        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(replayPeriod.multipliedBy(2), replayPeriod.multipliedBy(2)),
            null,
            configResourcesHolder
        )

        val messageIdAfterUpdate = UUID.randomUUID().toString()
        replayManager.addForReplay(
            0,
            messageIdAfterUpdate,
            messageIdAfterUpdate,
            sessionKey
        )

        eventually(5.seconds, 5.millis) {
            assertThat(tracker.numberOfReplays.containsKey(messageIdAfterUpdate))
        }

        replayManager.stop()
    }

    @Test
    fun `The ReplayScheduler replays at most 100 messages at a time`() {
        val total = 100
        val firstBatchLatch = CountDownLatch(total)
        val secondBatchLatch = CountDownLatch(total * 2)
        val replayedMessages = ConcurrentHashMap.newKeySet<String>()
        fun onReplay(messageId: String) {
            if (!replayedMessages.contains(messageId)) {
                firstBatchLatch.countDown()
                secondBatchLatch.countDown()
                replayedMessages.add(messageId)
            }
        }

        val replayManager = ReplayScheduler(coordinatorFactory, service, REPLAY_PERIOD_KEY, ::onReplay) { 0 }
        replayManager.start()
        setRunning()
        createResources(resourcesHolder)
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig(Duration.ofMillis(100), Duration.ofMillis(100)),
            null,
            configResourcesHolder
        )
        val addedMessages = mutableListOf<String>()
        for (i in 0 until 2 * total) {
            val messageId = UUID.randomUUID().toString()
            replayManager.addForReplay(0, messageId, messageId, sessionKey)
            addedMessages.add(messageId)
        }
        firstBatchLatch.await()
        for (i in 0 until total) {
            replayManager.removeFromReplay(addedMessages[i], sessionKey)
        }
        secondBatchLatch.await()
        for (i in total until 2 * total) {
            replayManager.removeFromReplay(addedMessages[i], sessionKey)
        }
    }

    class TrackReplayedMessages(numReplayedMessages: Int, private val totalNumberOfExceptions: Int = 0) {
        private val latch = CountDownLatch(numReplayedMessages)
        val numberOfReplays = ConcurrentHashMap<String, Int>()
        private var numberOfExceptions = 0

        fun replayMessage(message: String) {
            if (numberOfExceptions < totalNumberOfExceptions) {
                numberOfExceptions++
                latch.countDown()
                throw MyException()
            }
            val replays = numberOfReplays.compute(message) { _, numberOfReplays ->
                (numberOfReplays ?: 0) + 1
            }
            if (replays == 1) latch.countDown()
        }

        class MyException: Exception("Ohh No")

        fun await() {
            latch.await()
        }
    }

    private fun setRunning() {
        whenever(dominoTile.constructed().first().isRunning).doReturn(true)
    }
}