package net.corda.p2p.linkmanager.delivery

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.MockTimeFacilitiesProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.*

class ReplaySchedulerTest {

    companion object {
        private const val MAX_REPLAYING_MESSAGES = 100
        private val replayPeriod = Duration.ofMillis(2)
        private val sessionCounterparties = SessionManager.SessionCounterparties(
            createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", DeliveryTrackerTest.groupId),
            createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", DeliveryTrackerTest.groupId)
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
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        configHandler = context.arguments()[6] as ReplayScheduler<*>.ReplaySchedulerConfigurationChangeHandler
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
        val replayManager = ReplayScheduler(coordinatorFactory, service, false,
            { _: Any -> }, clock = mockTimeFacilitiesProvider.clock)
        assertThrows<IllegalStateException> {
            replayManager.addForReplay(0,"", Any(), Mockito.mock(SessionManager.SessionCounterparties::class.java))
        }
    }

    @Test
    fun `fromConfig correctly passes constant config`() {
        val replayScheduler = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            { _: Any -> },
            clock = mockTimeFacilitiesProvider.clock
        )
        val inner = ConfigFactory.empty()
            .withValue(LinkManagerConfiguration.MESSAGE_REPLAY_PERIOD_KEY, ConfigValueFactory.fromAnyRef(replayPeriod))
        val config = ConfigFactory.empty()
            .withValue(
                LinkManagerConfiguration.REPLAY_ALGORITHM_KEY,
                ConfigFactory.empty().withValue(
                    LinkManagerConfiguration.ReplayAlgorithm.Constant.configKeyName(), inner.root()
                ).root()
            )
            .withValue(LinkManagerConfiguration.MAX_REPLAYING_MESSAGES_PER_PEER, ConfigValueFactory.fromAnyRef(MAX_REPLAYING_MESSAGES)
        )
        val replaySchedulerConfig = replayScheduler.fromConfig(config)
            as ReplayScheduler.ReplaySchedulerConfig.ConstantReplaySchedulerConfig
        assertThat(replaySchedulerConfig.replayPeriod).isEqualTo(replayPeriod)
        assertThat(replaySchedulerConfig.maxReplayingMessages).isEqualTo(MAX_REPLAYING_MESSAGES)
    }

    @Test
    fun `fromConfig correctly passes exponential backoff config`() {
        val cutOff = Duration.ofDays(6)
        val replayScheduler = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            { _: Any -> },
            clock = mockTimeFacilitiesProvider.clock
        )
        val inner = ConfigFactory.empty()
            .withValue(LinkManagerConfiguration.BASE_REPLAY_PERIOD_KEY, ConfigValueFactory.fromAnyRef(replayPeriod))
            .withValue(LinkManagerConfiguration.REPLAY_PERIOD_CUTOFF_KEY, ConfigValueFactory.fromAnyRef(cutOff))
        val config = ConfigFactory.empty()
            .withValue(
                LinkManagerConfiguration.REPLAY_ALGORITHM_KEY,
                ConfigFactory.empty().withValue(
                    LinkManagerConfiguration.ReplayAlgorithm.ExponentialBackoff.configKeyName(), inner.root()
                ).root()
            )
            .withValue(LinkManagerConfiguration.MAX_REPLAYING_MESSAGES_PER_PEER, ConfigValueFactory.fromAnyRef(MAX_REPLAYING_MESSAGES))
        val replaySchedulerConfig = replayScheduler.fromConfig(config)
                as ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig
        assertThat(replaySchedulerConfig.baseReplayPeriod).isEqualTo(replayPeriod)
        assertThat(replaySchedulerConfig.cutOff).isEqualTo(cutOff)
        assertThat(replaySchedulerConfig.maxReplayingMessages).isEqualTo(MAX_REPLAYING_MESSAGES)
    }

    @Test
    fun `on applyNewConfiguration completes the future exceptionally if config is invalid`() {
        ReplayScheduler(coordinatorFactory, service, false, { _: Any -> }, clock = mockTimeFacilitiesProvider.clock)
        val future = configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                Duration.ofMillis(-10),
                Duration.ofMillis(-10),
                MAX_REPLAYING_MESSAGES
            ),
            null,
            configResourcesHolder
        )

        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isTrue
    }

    @Test
    fun `on applyNewConfiguration completes the future if config is valid`() {
        ReplayScheduler(coordinatorFactory, service, false, { _: Any -> }, clock = mockTimeFacilitiesProvider.clock)
        val future = configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                replayPeriod,
                replayPeriod,
                MAX_REPLAYING_MESSAGES
            ),
            null,
            configResourcesHolder
        )

        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `The ReplayScheduler replays added messages repeatedly`() {
        val messages = 9

        val tracker = TrackReplayedMessages()
        val replayScheduler = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.clock)
        setRunning()
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                replayPeriod,
                replayPeriod,
                MAX_REPLAYING_MESSAGES
            ),
            null,
            configResourcesHolder
        )

        val messageIds =  mutableListOf<String>()
        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            replayScheduler.addForReplay(
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

        replayScheduler.stop()
    }

    @Test
    fun `The ReplayScheduler stops replaying messages after removeAllMessages`() {
        val messages = 9

        val tracker = TrackReplayedMessages()
        val replayScheduler = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.clock)
        setRunning()
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                replayPeriod,
                replayPeriod,
                MAX_REPLAYING_MESSAGES
            ),
            null,
            configResourcesHolder
        )

        val messageIds = (1..messages).map { UUID.randomUUID().toString() }
        messageIds.forEach { replayScheduler.addForReplay(0, it, it, sessionCounterparties) }
        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        assertThat(tracker.messages).containsExactlyInAnyOrderElementsOf(messageIds)
        tracker.messages.clear()
        replayScheduler.removeAllMessagesFromReplay()
        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        assertThat(tracker.messages).isEmpty()
    }

    @Test
    fun `The ReplayScheduler doesn't replay removed messages`() {
        val messages = 8

        val tracker = TrackReplayedMessages()
        val replayScheduler = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.clock)
        setRunning()
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                replayPeriod,
                replayPeriod,
                MAX_REPLAYING_MESSAGES
            ),
            null,
            configResourcesHolder
        )

        val messageIdsToRemove = (1..messages).map { UUID.randomUUID().toString() }
        val messageIdsToNotRemove = (1..messages).map { UUID.randomUUID().toString() }
        messageIdsToRemove.forEach { replayScheduler.addForReplay(0, it, it, sessionCounterparties) }
        messageIdsToNotRemove.forEach { replayScheduler.addForReplay(0, it, it, sessionCounterparties) }

        //Acknowledge all even messages
        messageIdsToRemove.forEach {
            replayScheduler.removeFromReplay(it, sessionCounterparties)
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

        val replayScheduler = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            ::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.clock)
        replayScheduler.start()
        setRunning()
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                replayPeriod,
                replayPeriod,
                MAX_REPLAYING_MESSAGES
            ),
            null,
            configResourcesHolder
        )

        replayScheduler.addForReplay(0, "", message, sessionCounterparties)
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
        val replayScheduler = ReplayScheduler(
            coordinatorFactory,
            service,
            false,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.clock)
        replayScheduler.start()
        setRunning()
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
            replayPeriod,
            replayPeriod,
            MAX_REPLAYING_MESSAGES
        ),
        null,
        configResourcesHolder)

        val messageId = UUID.randomUUID().toString()
        replayScheduler.addForReplay(0, messageId, messageId, sessionCounterparties)
        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        tracker.messages.clear()
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                replayPeriod,
                replayPeriod,
                2 * MAX_REPLAYING_MESSAGES
            ),
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                replayPeriod,
                replayPeriod,
                MAX_REPLAYING_MESSAGES
            ),
        configResourcesHolder)

        val messageIdAfterUpdate = UUID.randomUUID().toString()
        replayScheduler.addForReplay(
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
        val replayScheduler = ReplayScheduler(
            coordinatorFactory,
            service,
            true,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.clock)
        replayScheduler.start()
        setRunning()
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                replayPeriod,
                replayPeriod,
                messageCap
            ),
            null,
            configResourcesHolder
        )

        val messageId = UUID.randomUUID().toString()
        replayScheduler.addForReplay(0, messageId, messageId, sessionCounterparties)

        val queuedMessageId = UUID.randomUUID().toString()
        replayScheduler.addForReplay(0, queuedMessageId, queuedMessageId, sessionCounterparties)
        replayScheduler.removeFromReplay(queuedMessageId, sessionCounterparties)

        val anotherQueuedMessageId = UUID.randomUUID().toString()
        replayScheduler.addForReplay(0, anotherQueuedMessageId, anotherQueuedMessageId, sessionCounterparties)

        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        replayScheduler.removeFromReplay(messageId, sessionCounterparties)
        mockTimeFacilitiesProvider.advanceTime(replayPeriod)

        assertThat(tracker.messages).containsOnly(messageId, anotherQueuedMessageId)
    }

    @Test
    fun `If the ReplayScheduler cap increases queued messages are replayed`() {
        val messageCap = 3
        val tracker = TrackReplayedMessages()
        val replayScheduler = ReplayScheduler(
            coordinatorFactory,
            service,
            true,
            tracker::replayMessage,
            {mockTimeFacilitiesProvider.mockScheduledExecutor},
            clock = mockTimeFacilitiesProvider.clock)
        replayScheduler.start()
        setRunning()
        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                replayPeriod,
                replayPeriod,
                MAX_REPLAYING_MESSAGES
            ),            null,
            configResourcesHolder
        )

        val messageIds = (1..2 * messageCap).map { UUID.randomUUID().toString() }
        messageIds.forEach { replayScheduler.addForReplay(0, it, it, sessionCounterparties) }

        mockTimeFacilitiesProvider.advanceTime(replayPeriod)
        tracker.messages.clear()

        configHandler.applyNewConfiguration(
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                replayPeriod,
                replayPeriod,
                2 * messageCap
            ),
            ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
                replayPeriod,
                replayPeriod,
                messageCap
            ),
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
