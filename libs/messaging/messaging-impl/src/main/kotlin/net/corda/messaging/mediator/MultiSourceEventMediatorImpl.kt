package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MediatorProducer
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.config.MediatorProducerConfig
import net.corda.messaging.api.mediator.statemanager.StateManager
import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.api.mediator.taskmanager.TaskType
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID

@Suppress("LongParameterList")
class MultiSourceEventMediatorImpl<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    private val serializer: CordaAvroSerializer<Any>,
    private val stateDeserializer: CordaAvroDeserializer<S>,
    private val stateManager: StateManager,
    private val taskManager: TaskManager,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
): MultiSourceEventMediator<K, S, E> {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.name}")

    private var consumers = listOf<MediatorConsumer<K, E>>()
    private var producers = listOf<MediatorProducer>()
    private val pollTimeoutInNanos = Duration.ofMillis(100).toNanos() // TODO take from config
    private val uniqueId = UUID.randomUUID().toString()
    private val lifecycleCoordinatorName = LifecycleCoordinatorName(
        "MultiSourceEventMediator--${config.name}", uniqueId
    )
    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator(lifecycleCoordinatorName) { _, _ -> }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinatorName

    override fun start() {
        log.debug { "Starting multi-source event mediator with config: $config" }
        lifecycleCoordinator.start()
        taskManager.execute(TaskType.LONG_RUNNING, ::run)
    }

    private fun stop() =
        Thread.currentThread().interrupt()
    private fun stopped() =
        Thread.currentThread().isInterrupted

    /**
     * This method is for closing the loop/thread externally. From inside the loop use the private [stopConsumeLoop].
     */
    override fun close() {
        lifecycleCoordinator.close()
    }

    private fun run() {
        var attempts = 0

        while (!stopped()) {
            attempts++
            try {
                createConsumers()
                createProducers()

                consumers.forEach{ it.subscribe() }
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)

                while (!stopped()) {
                    // TODO processEvents()
                }

            } catch (ex: Exception) {
                when (ex) {
                    is InterruptedException -> {
                        // Stopped
                    }
                    is CordaMessageAPIIntermittentException -> {
                        log.warn(
                            "${ex.message} Attempts: $attempts. Recreating consumers/producers and Retrying.", ex
                        )
                    }
                    else -> {
                        log.error(
                            "${ex.message} Attempts: $attempts. Closing subscription.", ex
                        )
                        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, "Error: ${ex.message}")
                        stop()
                    }
                }
            } finally {
                closeConsumersAndProducers()
            }
        }
        closeConsumersAndProducers()
    }

    private fun onSerializationError(event: ByteArray) {
        log.info("Error serializing [$event]")
        TODO()
    }

    private fun createConsumers() {
        check (config.consumerFactories.isNotEmpty()) {
            "None consumer factory set in configuration"
        }
        consumers = config.consumerFactories.map { consumerFactory ->
            consumerFactory.create(
                MediatorConsumerConfig(
                    config.messageProcessor.keyClass,
                    config.messageProcessor.eventValueClass,
                    ::onSerializationError
                )
            )
        }
    }

    private fun createProducers() {
        check (config.producerFactories.isNotEmpty()) {
            "None producer factory set in configuration"
        }
        producers = config.producerFactories.map { producerFactory ->
            producerFactory.create(
                MediatorProducerConfig(
                    ::onSerializationError
                )
            )
        }
    }

    private fun closeConsumersAndProducers() {
        consumers.forEach { it.close() }
        producers.forEach { it.close() }
    }

    private fun processEvents(isTaskStopped: Boolean) {
        var attempts = 0
        while (!isTaskStopped) {
            try {
                log.debug { "Polling and processing events" }
                val messages = poll(pollTimeoutInNanos)
                val msgGroups = messages.groupBy { it.key }
//                val states = stateManager.get(
//                    config.messageProcessor.stateValueClass, msgGroups.keys.mapTo(HashSet()) { it.toString() }
//                )
                val processorTasks =  msgGroups.map { msgGroup ->
                    val key = msgGroup.key.toString()
                    val events = msgGroup.value.map { it }
                    ProcessorTask(
                        key,
//                        states[key],
                        events,
                        config.messageProcessor,
                        stateManager,
                        serializer,
                        stateDeserializer,
                    )
                }

                processorTasks.map { processorTask ->
                    taskManager.execute(TaskType.SHORT_RUNNING, processorTask::run)
                        .thenApply { //outputEvents ->
                            // TODO
                        }
                }
//                val commitResults = consumers.map { consumer ->
//                    consumer.commitAsync()
//                }

            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        attempts++
                        // TODO handleProcessEventRetries(attempts, ex)
                    }

                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Multi-source event mediator ${config.name} failed to process messages, " +
                            "Fatal error occurred.", ex
                        )
                    }
                }
            }
        }
    }

    private fun poll(pollTimeoutInNanos: Long):  List<CordaConsumerRecord<K, E>> {
        val maxEndTime = System.nanoTime() + pollTimeoutInNanos
        return consumers.map { consumer ->
            val remainingTime = (maxEndTime - System.nanoTime()).coerceAtLeast(0)
            consumer.poll(Duration.ofNanos(remainingTime))
        }.flatten()
    }

//    private fun generateDeadLetterRecord(event: CordaConsumerRecord<K, E>, state: S?): Record<*, *> {
//        val keyBytes = ByteBuffer.wrap(cordaAvroSerializer.serialize(event.key))
//        val stateBytes =
//            if (state != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(state)) else null
//        val eventValue = event.value
//        val eventBytes =
//            if (eventValue != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(eventValue)) else null
//        return Record(
//            Schemas.getDLQTopic(eventTopic), event.key,
//            StateAndEventDeadLetterRecord(clock.instant(), keyBytes, stateBytes, eventBytes)
//        )
//    }
}