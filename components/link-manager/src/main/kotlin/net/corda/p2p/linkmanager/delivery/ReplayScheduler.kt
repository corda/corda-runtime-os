package net.corda.p2p.linkmanager.delivery

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.BASE_REPLAY_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.REPLAY_PERIOD_CUTOFF_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_REPLAYING_MESSAGES_PER_PEER
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.REPLAY_PERIOD_KEY
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.AutoClosableScheduledExecutorService
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

/**
 * This class keeps track of messages which may need to be replayed.
 */
@Suppress("LongParameterList")
internal class ReplayScheduler<M>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configReadService: ConfigurationReadService,
    private val limitTotalReplays: Boolean,
    private val replayMessage: (message: M) -> Unit,
    private val executorServiceFactory: () -> ScheduledExecutorService = { Executors.newSingleThreadScheduledExecutor() },
    private val currentTimestamp: () -> Long = { Instant.now().toEpochMilli() },
    ) : LifecycleWithDominoTile {

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        ::createResources,
        configurationChangeHandler = ReplaySchedulerConfigurationChangeHandler()
    )

    @Volatile
    private lateinit var executorService: ScheduledExecutorService

    private val replayCalculator = AtomicReference<ReplayCalculator>()
    data class ReplayInfo(val currentReplayPeriod: Duration, val future: ScheduledFuture<*>)
    // Compute on this map is used during add/remove operations to ensure these are performed atomically.
    private val replayingMessageIdsPerSessionCounterparties =
        ConcurrentHashMap<SessionManager.SessionCounterparties, MutableSet<MessageId>>()
    private val replayInfoPerMessageId = ConcurrentHashMap<MessageId, ReplayInfo>()
    data class QueuedMessage<M>(val originalAttemptTimestamp: Long, val uniqueId: MessageId, val message: M)
    private val queuedMessagesPerSessionCounterparties = ConcurrentHashMap<SessionManager.SessionCounterparties, MessageQueue<M>>()

    /**
     * A Queue of QueuedMessages where messages can be removed from the queue by messageId.
     */
    internal class MessageQueue<M> {

        private val queue: LinkedHashMap<MessageId, QueuedMessage<M>> = LinkedHashMap()

        fun queueMessage(message: QueuedMessage<M>) {
            synchronized(this) {
                queue.put(message.uniqueId, message)
            }
        }

        fun removeMessage(messageId: MessageId) {
            synchronized(this) {
                queue.remove(messageId)
            }
        }

        fun poll(): QueuedMessage<M>? {
            return synchronized(this) {
                val (id, message) = queue.entries.firstOrNull() ?: return null
                queue.remove(id)
                message
            }
        }
    }

    companion object {
        private val logger = contextLogger()
    }

    /**
     * Controls how added messages are replayed.
     * [maxReplayingMessages] The maximum number of replaying messages for each [SessionManager.SessionCounterparties]. This limit is only
     * applied if [limitTotalReplays] is true.
     */
    sealed class ReplaySchedulerConfig(open val maxReplayingMessages: Int) {

        /**
         * Schedule messages for replay with a constant delay between subsequent replays.
         * [replayPeriod] The period between replays.
         */
        data class ConstantReplaySchedulerConfig(
            val replayPeriod: Duration,
            override val maxReplayingMessages: Int
        ): ReplaySchedulerConfig(maxReplayingMessages) {
            constructor(config: Config, innerConfig: Config): this(
                innerConfig.getDuration(REPLAY_PERIOD_KEY),
                config.getInt(MAX_REPLAYING_MESSAGES_PER_PEER)
            )
        }

        /**
         * Schedule messages for replay with exponential backoff.
         * [baseReplayPeriod] The period between the original message being sent and a replay. This will double on every subsequent replay,
         * until [cutOff] is reached.
         * [cutOff] The maximum period between two replays of the same message.
         */
        data class ExponentialBackoffReplaySchedulerConfig(
            val baseReplayPeriod: Duration,
            val cutOff: Duration,
            override val maxReplayingMessages: Int
        ): ReplaySchedulerConfig(maxReplayingMessages) {
            constructor(config: Config, innerConfig: Config): this(
                innerConfig.getDuration(BASE_REPLAY_PERIOD_KEY),
                innerConfig.getDuration(REPLAY_PERIOD_CUTOFF_KEY),
                config.getInt(MAX_REPLAYING_MESSAGES_PER_PEER)
            )
        }
    }

    inner class ReplaySchedulerConfigurationChangeHandler: ConfigurationChangeHandler<ReplaySchedulerConfig>(configReadService,
        LinkManagerConfiguration.CONFIG_KEY,
        ::fromConfig) {
        override fun applyNewConfiguration(
            newConfiguration: ReplaySchedulerConfig,
            oldConfiguration: ReplaySchedulerConfig?,
            resources: ResourcesHolder,
        ): CompletableFuture<Unit> {
            val configUpdateResult = CompletableFuture<Unit>()
            when (newConfiguration) {
                is ReplaySchedulerConfig.ConstantReplaySchedulerConfig -> {
                    if (newConfiguration.replayPeriod.isNegative) {
                        configUpdateResult.completeExceptionally(
                            IllegalArgumentException("The duration configurations (with key $REPLAY_PERIOD_KEY) must be positive.")
                        )
                        return configUpdateResult
                    }
                    replayCalculator.set(ConstantReplayCalculator(limitTotalReplays, newConfiguration))
                }
                is ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig -> {
                    if (newConfiguration.baseReplayPeriod.isNegative || newConfiguration.cutOff.isNegative) {
                        configUpdateResult.completeExceptionally(
                            IllegalArgumentException("The duration configurations (with keys $BASE_REPLAY_PERIOD_KEY and " +
                                "$REPLAY_PERIOD_CUTOFF_KEY) must be positive.")
                        )
                        return configUpdateResult
                    }
                    replayCalculator.set(ExponentialBackoffReplayCalculator(limitTotalReplays, newConfiguration))
                }
            }
            val extraMessages = oldConfiguration?.maxReplayingMessages?.let { replayCalculator.get().extraMessagesToReplay(it) } ?: 0
            queueExtraMessages(extraMessages)
            configUpdateResult.complete(Unit)
            return configUpdateResult
        }
    }

    private fun queueExtraMessages(extraMessages: Int) {
        queuedMessagesPerSessionCounterparties.forEach { (sessionCounterparties, queuedMessages) ->
            for (i in 0 until extraMessages) {
                queuedMessages.poll()?.let {
                    addForReplay(it.originalAttemptTimestamp, it.uniqueId, it.message, sessionCounterparties)
                }
            }
        }
    }

    @VisibleForTesting
    internal fun fromConfig(config: Config): ReplaySchedulerConfig {
        for (replayAlgorithm in LinkManagerConfiguration.ReplayAlgorithm.values()) {
            if (config.hasPath(replayAlgorithm.configKeyName())) {
                val innerConfig = config.getConfig(replayAlgorithm.configKeyName())
                return when (replayAlgorithm) {
                    LinkManagerConfiguration.ReplayAlgorithm.Constant -> {
                        ReplaySchedulerConfig.ConstantReplaySchedulerConfig(config, innerConfig)
                    }
                    LinkManagerConfiguration.ReplayAlgorithm.ExponentialBackoff -> {
                        ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(config, innerConfig)
                    }
                }
            }
        }
        throw ConfigException.Missing("Expected config to contain the one of the following paths: " +
                "${LinkManagerConfiguration.ReplayAlgorithm.values().map { it.configKeyName() }}.")
    }

    private fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        executorService = executorServiceFactory()
        resources.keep(AutoClosableScheduledExecutorService(executorService))
        val future = CompletableFuture<Unit>()
        future.complete(Unit)
        return future
    }

    /**
     * Add a [message] to be replayed at certain points in the future. The number of messages replaying is capped per [counterparties],
     * if this is exceeded then messages are queued in the order they are added.
     */
    fun addForReplay(
        originalAttemptTimestamp: Long,
        messageId: MessageId,
        message: M,
        counterparties: SessionManager.SessionCounterparties
    ) {
        dominoTile.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("A message was added for replay before the ReplayScheduler was started.")
            }
            replayingMessageIdsPerSessionCounterparties.compute(counterparties) { _, replayingMessagesForSessionCounterparties ->
                when {
                    replayingMessagesForSessionCounterparties == null -> {
                        scheduleForReplay(originalAttemptTimestamp, messageId, message)
                        val set = ConcurrentHashMap.newKeySet<String>()
                        set.add(messageId)
                        set
                    }
                    replayCalculator.get().shouldReplayMessage(replayingMessagesForSessionCounterparties.size) -> {
                        scheduleForReplay(originalAttemptTimestamp, messageId, message)
                        replayingMessagesForSessionCounterparties.add(messageId)
                        replayingMessagesForSessionCounterparties
                    }
                    else -> {
                        queuedMessagesPerSessionCounterparties.computeIfAbsent(counterparties) { MessageQueue() }
                            .queueMessage(QueuedMessage(originalAttemptTimestamp, messageId, message))
                        replayingMessagesForSessionCounterparties
                    }
                }
            }
        }
    }

    private fun scheduleForReplay(originalAttemptTimestamp: Long, messageId: MessageId, message: M) {
        val firstReplayPeriod = replayCalculator.get().calculateReplayInterval()
        val delay = firstReplayPeriod.toMillis() + originalAttemptTimestamp - currentTimestamp()
        val future = executorService.schedule({ replay(message, messageId) }, delay, TimeUnit.MILLISECONDS)
        replayInfoPerMessageId[messageId] = ReplayInfo(firstReplayPeriod, future)
    }

    fun removeFromReplay(messageId: MessageId, counterparties: SessionManager.SessionCounterparties) {
        replayingMessageIdsPerSessionCounterparties.compute(counterparties) { _, replayingMessagesForSessionCounterparties ->
            val removed = replayInfoPerMessageId.remove(messageId)?.future?.cancel(false) ?: false
            replayingMessagesForSessionCounterparties?.remove(messageId)
            if (removed) {
                queuedMessagesPerSessionCounterparties[counterparties]?.poll()?.let {
                    addForReplay(it.originalAttemptTimestamp, it.uniqueId, it.message, counterparties)
                }
            } else {
                queuedMessagesPerSessionCounterparties[counterparties]?.removeMessage(messageId)
            }
            if (replayingMessagesForSessionCounterparties?.isEmpty() == true) {
                queuedMessagesPerSessionCounterparties.remove(counterparties)
                null
            } else {
                replayingMessagesForSessionCounterparties
            }
        }
    }

    fun removeAllMessagesFromReplay() {
        replayingMessageIdsPerSessionCounterparties.forEach { (sessionCounterparties, messageIds) ->
            messageIds.forEach { messageId ->
                removeFromReplay(messageId, sessionCounterparties)
            }
        }
    }

    private fun replay(message: M, messageId: MessageId) {
        try {
            replayMessage(message)
        } catch (exception: Exception) {
            val nextReplayInterval =
                replayInfoPerMessageId[messageId]?.let { replayCalculator.get().calculateReplayInterval(it.currentReplayPeriod).toMillis() }
            logger.error("An exception was thrown when replaying a message. The task will be retried again in $nextReplayInterval ms." +
                "\nException:",
                exception
            )
        }
        reschedule(message, messageId)
    }

    private fun reschedule(message: M, messageId: MessageId) {
        replayInfoPerMessageId.computeIfPresent(messageId) { _, oldReplayInfo ->
            val delay = replayCalculator.get().calculateReplayInterval(oldReplayInfo.currentReplayPeriod)
            ReplayInfo(
                delay,
                executorService.schedule({ replay(message, messageId) }, delay.toMillis(), TimeUnit.MILLISECONDS)
            )
        }
    }
}

internal typealias MessageId = String
