package net.corda.p2p.linkmanager.delivery

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.BASE_REPLAY_PERIOD_KEY_POSTFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.CUTOFF_REPLAY_KEY_POSTFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_REPLAYING_MESSAGES_PER_PEER_POSTFIX
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.AutoClosableScheduledExecutorService
import net.corda.v5.base.util.contextLogger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * This class keeps track of messages which may need to be replayed.
 */
@Suppress("LongParameterList")
internal class ReplayScheduler<M>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configReadService: ConfigurationReadService,
    private val limitTotalReplays: Boolean,
    private val replaySchedulerConfigKey: String,
    private val replayMessage: (message: M) -> Unit,
    private val currentTimestamp: () -> Long = { Instant.now().toEpochMilli() },
    ) : LifecycleWithDominoTile {

    override val dominoTile = DominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        ::createResources,
        configurationChangeHandler = ReplaySchedulerConfigurationChangeHandler()
    )

    @Volatile
    private lateinit var executorService: ScheduledExecutorService

    private val replayCalculator = AtomicReference<ReplayCalculator>()
    data class ReplayInfo(val currentReplayPeriod: Duration, val future: ScheduledFuture<*>)
    private val replayingMessageIdsPerSessionKey = ConcurrentHashMap<SessionManager.SessionKey, MutableSet<MessageId>>()
    private val replayInfoPerMessageId = ConcurrentHashMap<MessageId, ReplayInfo>()
    data class QueuedMessage<M>(val originalAttemptTimestamp: Long, val uniqueId: MessageId, val message: M)
    private val queuedMessagesPerSessionKey = ConcurrentHashMap<SessionManager.SessionKey, ConcurrentLinkedQueue<QueuedMessage<M>>>()

    companion object {
        private val logger = contextLogger()
    }

    /**
     * Controls how added messages are replayed.
     * [baseReplayPeriod] The period between the original message being sent and a replay. This will double on every subsequent replay,
     * until [cutOff] is reached.
     * [cutOff] The maximum period between two replays of the same message.
     * [maxReplayingMessages] The maximum number of replaying messages for each [SessionManager.SessionKey]. This limit is only applied
     * if [limitTotalReplays] is true.
     */
    internal data class ReplaySchedulerConfig(
        val baseReplayPeriod: Duration,
        val cutOff: Duration,
        val maxReplayingMessages: Int
    )

    inner class ReplaySchedulerConfigurationChangeHandler: ConfigurationChangeHandler<ReplaySchedulerConfig>(configReadService,
        LinkManagerConfiguration.CONFIG_KEY,
        ::fromConfig) {
        override fun applyNewConfiguration(
            newConfiguration: ReplaySchedulerConfig,
            oldConfiguration: ReplaySchedulerConfig?,
            resources: ResourcesHolder,
        ): CompletableFuture<Unit> {
            val configUpdateResult = CompletableFuture<Unit>()
            if (newConfiguration.baseReplayPeriod.isNegative || newConfiguration.cutOff.isNegative) {
                configUpdateResult.completeExceptionally(
                    IllegalArgumentException("The duration configurations (with keys " +
                        "$replaySchedulerConfigKey$BASE_REPLAY_PERIOD_KEY_POSTFIX and $replaySchedulerConfigKey$CUTOFF_REPLAY_KEY_POSTFIX" +
                         ") must be positive.")
                )
                return configUpdateResult
            }
            val newReplayCalculator = ReplayCalculator(limitTotalReplays, newConfiguration)
            replayCalculator.set(newReplayCalculator)
            val extraMessages = oldConfiguration?.maxReplayingMessages?.let { newReplayCalculator.extraMessagesToReplay(it) } ?: 0
            queuedMessagesPerSessionKey.forEach { (sessionKey, queuedMessages) ->
                for (i in 0 until extraMessages) {
                    queuedMessages.poll()?.let { addForReplay(it.originalAttemptTimestamp, it.uniqueId, it.message, sessionKey) }
                }
            }
            configUpdateResult.complete(Unit)
            return configUpdateResult
        }
    }

    private fun fromConfig(config: Config): ReplaySchedulerConfig {
        return ReplaySchedulerConfig(
            config.getDuration(replaySchedulerConfigKey + BASE_REPLAY_PERIOD_KEY_POSTFIX),
            config.getDuration(replaySchedulerConfigKey + CUTOFF_REPLAY_KEY_POSTFIX),
            config.getInt(replaySchedulerConfigKey + MAX_REPLAYING_MESSAGES_PER_PEER_POSTFIX)
        )
    }

    private fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        executorService = Executors.newSingleThreadScheduledExecutor()
        resources.keep(AutoClosableScheduledExecutorService(executorService))
        val future = CompletableFuture<Unit>()
        future.complete(Unit)
        return future
    }

    /**
     * Add a [message] to be replayed at certain points in the future. The number of messages replaying is capped per [sessionKey],
     * if this is exceeded then messages are queued in the order they are added.
     */
    fun addForReplay(originalAttemptTimestamp: Long, messageId: MessageId, message: M, sessionKey: SessionManager.SessionKey) {
        dominoTile.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("A message was added for replay before the ReplayScheduler was started.")
            }
            replayingMessageIdsPerSessionKey.compute(sessionKey) { _, replayingMessagesForSessionKey ->
                when {
                    replayingMessagesForSessionKey == null -> {
                        scheduleForReplay(originalAttemptTimestamp, messageId, message)
                        val set = ConcurrentHashMap.newKeySet<String>()
                        set.add(messageId)
                        set
                    }
                    replayCalculator.get().shouldReplayMessage(replayingMessagesForSessionKey.size) -> {
                        scheduleForReplay(originalAttemptTimestamp, messageId, message)
                        replayingMessagesForSessionKey.add(messageId)
                        replayingMessagesForSessionKey
                    }
                    else -> {
                        queuedMessagesPerSessionKey.computeIfAbsent(sessionKey) { ConcurrentLinkedQueue() }
                            .add(QueuedMessage(originalAttemptTimestamp, messageId, message))
                        replayingMessagesForSessionKey
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

    fun removeFromReplay(messageId: MessageId, sessionKey: SessionManager.SessionKey) {
        replayingMessageIdsPerSessionKey.compute(sessionKey) { _, replayingMessagesForSessionKey ->
            val removed = replayInfoPerMessageId.remove(messageId)?.future?.cancel(false) ?: false
            replayingMessagesForSessionKey?.remove(messageId)
            if (removed) {
                queuedMessagesPerSessionKey[sessionKey]?.poll()?.let {
                    addForReplay(it.originalAttemptTimestamp, it.uniqueId, it.message, sessionKey)
                }
            }
            if (replayingMessagesForSessionKey?.isEmpty() == true) {
                queuedMessagesPerSessionKey.remove(sessionKey)
                null
            } else {
                replayingMessagesForSessionKey
            }
        }
    }

    fun removeAllMessagesFromReplay() {
        replayingMessageIdsPerSessionKey.forEach { (sessionKey, messageIds) ->
            messageIds.forEach { messageId ->
                removeFromReplay(messageId, sessionKey)
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
