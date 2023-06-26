package net.corda.messaging.subscription.consumer

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.lifecycle.Resource
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.constants.MetricsConstants
import net.corda.messaging.utils.tryGetResult
import net.corda.metrics.CordaMetrics
import net.corda.tracing.wrapWithTracingExecutor
import net.corda.utilities.debug
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.slf4j.LoggerFactory
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisCluster
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

@Suppress("LongParameterList", "TooManyFunctions")
internal class RedisStateAndEventConsumer<K : Any, S : Any, E : Any>(
    private val config: ResolvedSubscriptionConfig,
    override val eventConsumer: CordaConsumer<K, E>,
    override val stateConsumer: CordaConsumer<K, S>,
    private val partitionState: StateAndEventPartitionState<K, S>,
    private val stateAndEventListener: StateAndEventListener<K, S>?,
    private val avroSerializer: CordaAvroSerializer<Any>,
    private val avroDeserializer: CordaAvroDeserializer<Any>
): StateAndEventConsumer<K, S, E>, Resource {

    companion object {

        //short timeout for poll of paused partitions when waiting for processor to finish
        private val PAUSED_POLL_TIMEOUT = Duration.ofMillis(100)

        // Event poll timeout
        private val EVENT_POLL_TIMEOUT = Duration.ofMillis(100)
    }

    //single threaded executor per state and event consumer
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        val thread = Thread(runnable)
        thread.isDaemon = true
        thread
    }

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.clientId}")

//    var redisUri: RedisURI = RedisURI.builder()
//        .withHost("orr-memory-db.8b332u.clustercfg.memorydb.eu-west-2.amazonaws.com")
//        .withPort(6379)
//        .withDatabase(0)
//        .build()
//    var redisClient: RedisClient? = RedisClient.create(redisUri)
//    var connection = redisClient!!.connect(ByteArrayCodec())
//    var syncRedisCommands = connection.sync()

    private val hostAndPort = HostAndPort("orr-memory-db.8b332u.clustercfg.memorydb.eu-west-2.amazonaws.com", 6379).also {
        log.warn("Connecting to host ${it.host}, port ${it.port}")
    }
    private val jedisCluster = JedisCluster(Collections.singleton(hostAndPort), 5000, 5000, 2, null, null, GenericObjectPoolConfig(), false)
    private val maxPollInterval = config.processorTimeout.toMillis()
    private val initialProcessorTimeout = maxPollInterval / 4
    private var pollIntervalCutoff = 0L

    private val eventPollTimer = CordaMetrics.Metric.MessagePollTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.EVENT_POLL_OPERATION)
        .build()

    override fun onPartitionsAssigned(partitions: Set<CordaTopicPartition>) {
        // Let's do nothing
    }

    override fun onPartitionsRevoked(partitions: Set<CordaTopicPartition>) {
        // Let's do nothing
    }

    override fun pollEvents(): List<CordaConsumerRecord<K, E>> {
        return eventPollTimer.recordCallable {
            pollIntervalCutoff = getNextPollIntervalCutoff()
            eventConsumer.poll(EVENT_POLL_TIMEOUT).also {
                log.debug { "Received ${it.size} events on keys ${it.joinToString { it.key.toString() }}" }
            }
        }!!
    }

    override fun resetEventOffsetPosition() {
        log.debug { "Last committed offset position reset for the event consumer." }
        eventConsumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
    }

    override fun pollAndUpdateStates(syncPartitions: Boolean) {
        // Let's do nothing
    }

    override fun resetPollInterval() {
        // Let's do nothing
    }

    override fun waitForFunctionToFinish(
        function: () -> Any,
        maxTimeout: Long,
        timeoutErrorMessage: String
    ): CompletableFuture<Any> {
        val future: CompletableFuture<Any> = CompletableFuture.supplyAsync(
            function,
            wrapWithTracingExecutor(executor)
        )
        future.tryGetResult(getInitialConsumerTimeout())

        if (!future.isDone) {
            pauseEventConsumerAndWaitForFutureToFinish(future, maxTimeout)
        }

        if (!future.isDone) {
            future.cancel(true)
            log.error(timeoutErrorMessage)
        }

        return future
    }

    override fun updateInMemoryStatePostCommit(updatedStates: MutableMap<Int, MutableMap<K, S?>>, clock: Clock) {
        // You are stepping on a gold mine here!
        val updatedStatesByKey = mutableMapOf<K, S?>()
        updatedStates.forEach { (_, states) ->
            for (entry in states) {
                val key = entry.key
                val value = entry.value
                val keyBytes = avroSerializer.serialize(key)
                val stateBytes = if (value != null) {
                    avroSerializer.serialize(value)
                } else {
                    byteArrayOf()
                }
                jedisCluster.set(keyBytes, stateBytes)
                releaseLock(keyBytes!!)
                updatedStatesByKey[key] = value
            }
        }

        // No clue why this has to be here -_-
        stateAndEventListener?.onPostCommit(updatedStatesByKey)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getInMemoryStateValue(key: K): S? {
        // This is also a gold mine, just so you know :)
        val keyBytes = avroSerializer.serialize(key)
        acquireLock(keyBytes!!)
        val stateBytes = jedisCluster.get(keyBytes)
        return if (stateBytes != null) {
            avroDeserializer.deserialize(stateBytes) as? S
        } else {
            null
        }
    }

    override fun close() {
        eventConsumer.close()
        executor.shutdown()
//        connection.close();
//        redisClient?.shutdown();
        jedisCluster.close()
    }

    private fun acquireLock(key: ByteArray) {
        val lockKey = key + 0.toByte()
        var isLocked = jedisCluster.get(lockKey)
        while (isLocked != null && isLocked[0].toInt() == 1) {
            Thread.sleep(100)
            isLocked = jedisCluster.get(lockKey)
        }
        jedisCluster.set(lockKey, byteArrayOf((1).toByte()))
    }

    private fun releaseLock(key: ByteArray) {
        val lockKey = key + 0.toByte()
        jedisCluster.set(lockKey, byteArrayOf((0).toByte()))
    }

    /**
     * Helper method to poll events from a paused [eventConsumer], should only be used to prevent it from being kicked
     * out of the consumer group and only when all partitions are paused as to not lose any events.
     *
     * If a rebalance occurs during the poll, the [cleanUp] function is invoked and only [eventConsumer]'s partitions
     * matching the following conditions are resumed:
     *  - Not currently being synced.
     *  - Previously paused by the caller ([pausedPartitions]).
     *  If a partition not matching the above rules is wrongly resumed, events might be processed twice or for states
     *  not yet in sync.
     */
    private fun pollWithCleanUpAndExceptionOnRebalance(
        message: String,
        pausedPartitions: Set<CordaTopicPartition>,
        cleanUp: () -> Unit
    ) {
        partitionState.dirty = false
        eventConsumer.poll(PAUSED_POLL_TIMEOUT).forEach { event ->
            // Should not happen, the warning is left in place for easier troubleshooting in case it does.
            log.warn("Polling from paused eventConsumer has lost event with key: ${event.key}, this will likely " +
                    "cause execution problems for events with this id")
        }

        // Rebalance occurred: give up, nothing can be assumed at this point.
        if (partitionState.dirty) {
            partitionState.dirty = false
            cleanUp()

            // If we don't own the paused partitions anymore, they'll start as resumed on the new assigned consumer.
            // If we still own the paused partitions, resume them if and only if they are not currently being synced.
            val partitionsToResume = eventConsumer.assignment()
                // Only partitions previously paused by the caller
                .intersect(pausedPartitions)

            log.debug { "Rebalance occurred while polling from paused consumer, resuming partitions: $partitionsToResume" }
            eventConsumer.resume(partitionsToResume)

            throw StateAndEventConsumer.RebalanceInProgressException(message)
        }
    }

    private fun getNextPollIntervalCutoff(): Long {
        return System.currentTimeMillis() + (maxPollInterval / 2)
    }

    private fun getInitialConsumerTimeout(): Long {
        return if ((System.currentTimeMillis() + initialProcessorTimeout) > pollIntervalCutoff) {
            pollIntervalCutoff - System.currentTimeMillis()
        } else {
            initialProcessorTimeout
        }
    }

    private fun pauseEventConsumerAndWaitForFutureToFinish(future: CompletableFuture<*>, timeout: Long) {
        val pausePartitions = eventConsumer.assignment() - eventConsumer.paused()
        log.debug { "Pause partitions and wait for future to finish. Assignment: $pausePartitions" }
        eventConsumer.pause(pausePartitions)
        val maxWaitTime = System.currentTimeMillis() + timeout
        var done = future.isDone

        while (!done && (maxWaitTime > System.currentTimeMillis())) {
            pollWithCleanUpAndExceptionOnRebalance("Rebalance occurred while waiting for future to finish", pausePartitions) {
                future.cancel(true)
            }

            pollIntervalCutoff = getNextPollIntervalCutoff()
            pollAndUpdateStates(false)
            done = future.isDone
        }

        // Resume only those partitions currently assigned and previously paused.
        val partitionsToResume = eventConsumer.assignment().intersect(pausePartitions)
        log.debug { "Resume partitions. Finished wait for future[completed=${future.isDone}]. Assignment: $partitionsToResume" }
        eventConsumer.resume(partitionsToResume)
    }
}