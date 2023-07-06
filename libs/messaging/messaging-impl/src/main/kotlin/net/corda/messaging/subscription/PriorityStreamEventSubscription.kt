package net.corda.messaging.subscription

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.waitOnPublisherFutures
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.config.MessagingConfigResolver
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.constants.MetricsConstants
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.publisher.RestClient
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.utils.toCordaProducerRecords
import net.corda.messaging.utils.toRecord
import net.corda.messaging.utils.tryGetResult
import net.corda.metrics.CordaMetrics
import net.corda.schema.Schemas.Crypto.FLOW_OPS_MESSAGE_TOPIC
import net.corda.schema.Schemas.Persistence.PERSISTENCE_ENTITY_PROCESSOR_TOPIC
import net.corda.schema.Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC
import net.corda.schema.Schemas.UniquenessChecker.UNIQUENESS_CHECK_TOPIC
import net.corda.schema.Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC
import net.corda.tracing.wrapWithTracingExecutor
import net.corda.utilities.debug
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.slf4j.LoggerFactory
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisCluster
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.collections.ArrayDeque
import kotlin.random.Random

@Suppress("LongParameterList")
internal class PriorityStreamEventSubscription<K : Any, S : Any, E : Any>(
    private val subscriptionConfig: SubscriptionConfig,
    private val messagingConfig: SmartConfig,
    private val topics: Map<Int, List<String>>,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val cordaProducerBuilder: CordaProducerBuilder,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    private val cordaAvroDeserializer: CordaAvroDeserializer<Any>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : StateAndEventSubscription<K, S, E> {

    private val PAUSED_POLL_TIMEOUT = Duration.ofMillis(100)
    private val EVENT_POLL_TIMEOUT = Duration.ofMillis(1000)
    private val config: ResolvedSubscriptionConfig = getConfig(
        SubscriptionConfig("${subscriptionConfig.groupName}-default", "default"),
        messagingConfig)
    private val maxPollInterval = Duration.ofSeconds(30).toMillis()
    private val log = LoggerFactory.getLogger(javaClass.name)
    private var threadLooper =
        ThreadLooper(log, config, lifecycleCoordinatorFactory, "state/event processing thread", ::runConsumeLoop)
    private var producer: CordaProducer? = null
    private var consumers: MutableMap<Int, MutableList<CordaConsumer<K, E>>> = topics.map { it.key to mutableListOf<CordaConsumer<K, E>>() }.toMap().toMutableMap()
    private var consumersLastPoll: MutableMap<CordaConsumer<K, E>, Long> = mutableMapOf()
    private val priorities: List<Int> = consumers.keys.sorted()
    private val hostAndPort = HostAndPort("orr-memory-db.8b332u.clustercfg.memorydb.eu-west-2.amazonaws.com", 6379).also {
        log.warn("Connecting to host ${it.host}, port ${it.port}")
    }
    private val jedisCluster = JedisCluster(Collections.singleton(hostAndPort), 5000, 5000, 2, null, null, GenericObjectPoolConfig(), false)
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        val thread = Thread(runnable)
        thread.isDaemon = true
        thread
    }
    private val random = SecureRandom()

    private val processorMeter = CordaMetrics.Metric.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.BATCH_PROCESS_OPERATION)
        .build()

    private val batchSizeHistogram = CordaMetrics.Metric.MessageBatchSize.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .build()

    private val recordsAvoidedCount = CordaMetrics.Metric.RecordPublishAvoidedCount.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.Topic, config.topic)
        .build()

    private val eventPollTimer = CordaMetrics.Metric.MessagePollTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.EVENT_POLL_OPERATION)
        .build()

    private val attemptStateLockCounter = CordaMetrics.Metric.StateLockAttempts.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.EVENT_POLL_OPERATION)
        .withTag(CordaMetrics.Tag.Topic, config.topic)
        .build()

    private val acquiredStateLocksCounter = CordaMetrics.Metric.StateAcquiredLocksCount.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.EVENT_POLL_OPERATION)
        .withTag(CordaMetrics.Tag.Topic, config.topic)
        .build()

    private val releasedStateLocksCounter = CordaMetrics.Metric.StateReleasedLocksCount.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.EVENT_POLL_OPERATION)
        .withTag(CordaMetrics.Tag.Topic, config.topic)
        .build()

    private val topicToRestClient = mapOf(
        FLOW_OPS_MESSAGE_TOPIC to RestClient<FlowEvent>("http://corda-crypto-worker:8080", cordaAvroSerializer, cordaAvroDeserializer),
        PERSISTENCE_LEDGER_PROCESSOR_TOPIC to RestClient<FlowEvent>("http://corda-db-persistence-worker:8080", cordaAvroSerializer, cordaAvroDeserializer),
        PERSISTENCE_ENTITY_PROCESSOR_TOPIC to RestClient<FlowEvent>("http://corda-db-worker:8080", cordaAvroSerializer, cordaAvroDeserializer),
        UNIQUENESS_CHECK_TOPIC to RestClient<FlowEvent>("http://corda-db-uniqueness-worker:8080", cordaAvroSerializer, cordaAvroDeserializer),
        VERIFICATION_LEDGER_PROCESSOR_TOPIC to RestClient<FlowEvent>("http://corda-flow-verification-worker:8080", cordaAvroSerializer, cordaAvroDeserializer)
    )

    override val subscriptionName: LifecycleCoordinatorName
        get() = threadLooper.lifecycleCoordinatorName

    override fun start() {
        log.debug { "Starting subscription with config: $config" }
        threadLooper.start()
    }

    /**
     * This method is for closing the loop/thread externally. From inside the loop use the private [stopConsumeLoop].
     */
    override fun close() {
        shutdownResources()
        threadLooper.close()
        executor.shutdown()
        jedisCluster.close()
    }

    private fun setupResources() {
        val producerConfig = ProducerConfig(config.clientId, random.nextInt(), true, ProducerRoles.SAE_PRODUCER, false)
        producer = cordaProducerBuilder.createProducer(
            producerConfig,
            config.messageBusConfig
        ) { _ ->
            log.warn("Failed to serialize record!")
        }
        consumers.forEach {
            topics[it.key]?.forEach { topic ->
                val consumerConfig =
                    getConfig(SubscriptionConfig("${config.group}-${topic}", topic), messagingConfig)
                val eventConsumerConfig =
                    ConsumerConfig(consumerConfig.group, "${config.clientId}-eventConsumer", ConsumerRoles.SAE_EVENT)
                val consumer = cordaConsumerBuilder.createConsumer(
                    eventConsumerConfig,
                    consumerConfig.messageBusConfig,
                    processor.keyClass,
                    processor.eventValueClass,
                    { _ ->
                        log.error("Failed to deserialize event record!")
                    }
                )
                consumer.subscribe(topic)
                log.info("Assigned partitions for topic ${topics[it.key]} with: ${consumer.assignment()}")
                consumers[it.key]?.add(consumer)
            }
        }
    }

    private fun runConsumeLoop() {
        var attempts = 0
        while (!threadLooper.loopStopped) {
            attempts++
            try {
                setupResources()
                threadLooper.updateLifecycleStatus(LifecycleStatus.UP)
                processEvents()

            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn(
                            "${ex.message} - Attempts: $attempts. Recreating " +
                                    "consumer/producer and Retrying.", ex
                        )
                    }
                    else -> {
                        log.error(
                            "${ex.message} - Attempts: $attempts. Closing subscription.", ex
                        )
                        threadLooper.updateLifecycleStatus(LifecycleStatus.ERROR, ex.message!!)
                        threadLooper.stopLoop()
                    }
                }
            } finally {
                shutdownResources()
            }
        }
        shutdownResources()
    }

    private fun processEvents() {
        var attempts = 0
        while (!threadLooper.loopStopped) {
            try {
                log.debug { "Polling and processing events" }
                val records = getHighestPriorityEvents()
                for ((consumer, events) in records!!) {
                    batchSizeHistogram.record(events.size.toDouble())
                    log.debug { "Processing events(keys: ${events.joinToString { it.key.toString() }}, size: ${records.size})" }
                    val recordsQueue = ArrayDeque(events)
                    producer?.beginTransaction()
                    while (recordsQueue.isNotEmpty()) {
                        val event = recordsQueue.removeFirst()
                        processEvent(event)
                    }
                    producer?.sendRecordOffsetsToTransaction(consumer, events)
                    producer?.commitTransaction()
                }
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        attempts++
                        handleProcessEventRetries(attempts, ex)
                    }
                    is StateAndEventConsumer.RebalanceInProgressException -> {
                        log.warn ("Abandoning processing of events due to a rebalance", ex)
                    }
                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Failed to process records from group ${config.group}, " +
                                    "producerClientId ${config.clientId}. " +
                                    "Fatal error occurred.", ex
                        )
                    }
                }
            }
        }
    }

    private fun getHighestPriorityEvents() : MutableMap<CordaConsumer<K, E>, List<CordaConsumerRecord<K, E>>>? {
        return eventPollTimer.recordCallable {
            var recordsCount = 0
            val events = mutableMapOf<CordaConsumer<K, E>, List<CordaConsumerRecord<K, E>>>()
            for (priority in priorities) {
                if (recordsCount == 0) {
                    for (consumer in consumers[priority]!!) {
                        try {
                            val records = consumer.poll(EVENT_POLL_TIMEOUT)
                            events[consumer] = records
                            recordsCount += records.size
                            markConsumerPoll(consumer)
                            val partitions = consumer.assignment()
                            log.info("Polled (${records.size}) records from topics [${topics[priority]?.joinToString(", ")}] with [$partitions]")
                        } catch (ex: Exception) {
                            consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
                        }
                    }
                } else {
                    for (consumer in consumers[priority]!!) {
                        keepConsumersAlive(consumer, priority)
                    }
                }
            }
            events
        }
    }

    private fun keepConsumersAlive(consumer: CordaConsumer<K, E>, priority: Int) {
        val lastPoll = consumersLastPoll.getOrDefault(consumer, 0)
        val gracePeriod = lastPoll + maxPollInterval
        if (gracePeriod <= System.currentTimeMillis()) {
            try {
                var partitions = consumer.assignment()
                consumer.pause(partitions)
                consumer.poll(PAUSED_POLL_TIMEOUT)
                partitions = consumer.assignment()
                consumer.resume(partitions)
                markConsumerPoll(consumer)
                log.info("Triggered paused poll on consumer of priority $priority with partitions [$partitions]")
            } catch (ex: Exception) {
                consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
                log.error("Failed to trigger paused poll on consumer of priority $priority with message '${ex.message}'")
            }
        }
    }

    private fun markConsumerPoll(consumer: CordaConsumer<K, E>) {
        consumersLastPoll[consumer] = System.currentTimeMillis()
    }

    @Suppress("UNCHECKED_CAST")
    private fun processEvent(
        event: CordaConsumerRecord<K, E>
    ) {
        processorMeter.recordCallable {
            val subsequentEvents = ArrayDeque(listOf(event))
            val states = mutableMapOf<K, S?>()
            try {
                while (subsequentEvents.isNotEmpty()) {
                    val currentEvent = subsequentEvents.removeFirst()
                    log.info("Processing event: $currentEvent")
                    val state = states.computeIfAbsent(currentEvent.key) { getState(currentEvent.key) }
                    val future = waitForFunctionToFinish({ processor.onNext(state, currentEvent.toRecord()) },
                        config.processorTimeout.toMillis(),
                        "Processing event $currentEvent timed-out!")
                    val currentEventUpdates = future.tryGetResult() as StateAndEventProcessor.Response<S>
                    states[currentEvent.key] = currentEventUpdates.updatedState
                    // Get wake-up calls
                    val (wakeups, outputEvents) = currentEventUpdates.responseEvents.partition {
                        isWakeup(it)
                    }
                    // Get REST calls
                    val (overRestEvents, overKafkaEvents) = outputEvents.partition {
                        topicToRestClient.containsKey(it.topic)
                    }
                    // Execute REST calls
                    val responseEvents = overRestEvents.mapNotNull {
                        topicToRestClient[it.topic]?.publish(listOf(it))
                    }.flatten()
                    // Add subsequent events to be processed next in the queue
                    val eventsToProcess = (wakeups + responseEvents) as List<Record<K, E>> // + restResponses
                    subsequentEvents.addAll(eventsToProcess.map {
                        val ret = toCordaConsumerRecord(currentEvent, it)
                        ret
                    })
                    recordsAvoidedCount.increment(eventsToProcess.size.toDouble())
                    when {
                        currentEventUpdates.markForDLQ -> {
                            log.warn(
                                "Sending state and event on key ${currentEvent.key} for topic ${currentEvent.topic} to dead letter queue. " +
                                        "Processor marked event for the dead letter queue"
                            )
                            states[currentEvent.key] = null

                            // In this case the processor may ask us to publish some output records regardless, so make sure these
                            // are outputted.
                            producer?.sendRecords(overKafkaEvents.toCordaProducerRecords())
                        }

                        else -> {
                            producer?.sendRecords(overKafkaEvents.toCordaProducerRecords())
                            log.debug { "Completed event: $currentEvent" }
                        }
                    }
                    log.info("Processed event of key '${event.key}' successfully.")

                }
                states.forEach {
                    updateState(it.key, it.value)
                }
            }
            catch (ex: Exception) {
                producer?.abortTransaction()
                states.forEach {
                    releaseStateKey(it.key)
                }
            }
        }
    }

    /**
     * Handle retries for event processing.
     * Reset [nullableEventConsumer] position and retry poll and process of eventRecords
     * Retry a max of [ResolvedSubscriptionConfig.processorRetries] times.
     * If [ResolvedSubscriptionConfig.processorRetries] is exceeded then throw a [CordaMessageAPIIntermittentException]
     */
    private fun handleProcessEventRetries(
        attempts: Int,
        ex: Exception
    ) {
        if (attempts <= config.processorRetries) {
            log.warn(
                "Failed to process record from group ${config.group}, " +
                        "producerClientId ${config.clientId}. " +
                        "Retrying poll and process. Attempts: $attempts."
            )
            consumers.forEach {
                it.value.forEach { consumer ->
                    consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
                }
            }
        } else {
            val message = "Failed to process records from group ${config.group}, " +
                    "producerClientId ${config.clientId}. " +
                    "Attempts: $attempts. Max reties exceeded."
            log.warn(message, ex)
            throw CordaMessageAPIIntermittentException(message, ex)
        }
    }

    fun waitForFunctionToFinish(function: () -> Any, maxTimeout: Long, timeoutErrorMessage: String): CompletableFuture<Any> {
        val future: CompletableFuture<Any> = CompletableFuture.supplyAsync(
            function,
            executor
        )
        future.tryGetResult(maxTimeout)

        if (!future.isDone) {
            future.cancel(true)
            log.error(timeoutErrorMessage)
        }

        return future
    }

    private fun toCordaConsumerRecord(sourceRecord: CordaConsumerRecord<K, E>, newEvent : Record<K, E>) =
        CordaConsumerRecord(
            newEvent.topic,
            sourceRecord.partition,
            sourceRecord.offset,
            sourceRecord.key,
            newEvent.value,
            sourceRecord.timestamp,
            sourceRecord.headers
        )

    private fun isWakeup(record: Record<*, *>) : Boolean {
        return if ((record.value != null) && record.value is FlowEvent) {
            val flowEvent = record.value!! as FlowEvent
            flowEvent.payload is Wakeup
        } else {
            false
        }
    }

    private fun shutdownResources() {
        producer?.close()
        consumers.forEach { consumers ->
            consumers.value.forEach {
                it.close()
            }
        }
        consumers.clear()
        consumers = topics.map { it.key to mutableListOf<CordaConsumer<K, E>>() }.toMap().toMutableMap()
        producer = null
    }

    @Suppress("UNCHECKED_CAST")
    fun getState(key: K): S? {
        val keyBytes = cordaAvroSerializer.serialize(key)
        acquireLock(keyBytes!!)
        val stateBytes = jedisCluster.get(keyBytes)
        return if (stateBytes != null) {
            cordaAvroDeserializer.deserialize(stateBytes) as? S
        } else {
            null
        }
    }

    fun updateState(key: K, state: S?) {
        val keyBytes = cordaAvroSerializer.serialize(key)
        if (state != null) {
            val stateBytes = cordaAvroSerializer.serialize(state)
            jedisCluster.set(keyBytes, stateBytes)
            releaseLock(keyBytes!!)
        }
        else {
            jedisCluster.del(keyBytes)
            deleteLock(keyBytes!!)
        }
    }

    private fun acquireLock(key: ByteArray) {
        val lockKey = key + 0.toByte()
        var isLocked = jedisCluster.get(lockKey)
        while (isLocked != null && isLocked[0].toInt() == 1) {
            attemptStateLockCounter.increment()
            Thread.sleep(100)
            isLocked = jedisCluster.get(lockKey)
        }
        jedisCluster.set(lockKey, byteArrayOf((1).toByte()))
        acquiredStateLocksCounter.increment()
    }

    private fun releaseStateKey(key: K) {
        val keyBytes = cordaAvroSerializer.serialize(key)
        releaseLock(keyBytes!!)
    }

    private fun releaseLock(key: ByteArray) {
        val lockKey = key + 0.toByte()
        jedisCluster.set(lockKey, byteArrayOf((0).toByte()))
        releasedStateLocksCounter.increment()
    }

    private fun deleteLock(key: ByteArray) {
        val lockKey = key + 0.toByte()
        jedisCluster.del(lockKey)
    }

    private fun getConfig(
        subscriptionConfig: SubscriptionConfig,
        messagingConfig: SmartConfig
    ): ResolvedSubscriptionConfig {
        val configBuilder = MessagingConfigResolver(messagingConfig.factory)
        return configBuilder.buildSubscriptionConfig(
            SubscriptionType.STATE_AND_EVENT,
            subscriptionConfig,
            messagingConfig,
            UUID.randomUUID().toString()
        )
    }
}
