package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.subscription.consumer.builder.impl.CordaKafkaConsumerBuilderImpl
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.utils.render
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.apache.kafka.common.TopicPartition
import org.jboss.util.collection.WeakValueHashMap
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInput
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock


@Component
class CordaKafkaRPCSenderImpl<TREQ : Any, TRESP : Any>(
    private val rpcConfig: RPCConfig<TREQ, TRESP>,
    private val config: Config,
    private val publisher: Publisher,
    private val consumerBuilder: CordaKafkaConsumerBuilderImpl<String, RPCResponse>,
) : RPCSender<TREQ, TRESP>, RPCSubscription<TREQ, TRESP> {

    private companion object {
        private val log: Logger = contextLogger()
    }

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null
    private var partitions: List<TopicPartition> = listOf()
    private val futureMap: WeakValueHashMap<String, CompletableFuture<TRESP>> = WeakValueHashMap()

    override val isRunning: Boolean
        get() = !stopped

    private val consumerThreadStopTimeout = config.getLong(KafkaProperties.CONSUMER_THREAD_STOP_TIMEOUT)
    private val topicPrefix = config.getString(KafkaProperties.TOPIC_PREFIX)
    private val groupName = config.getString(KafkaProperties.CONSUMER_GROUP_ID)
    private val topic = config.getString(KafkaProperties.TOPIC_NAME)

    private val errorMsg = "Failed to read records from group $groupName, topic $topic"

    override fun start() {
        log.debug { "Starting subscription with config:\n${config.render()}" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                consumeLoopThread = thread(
                    start = true,
                    isDaemon = true,
                    contextClassLoader = null,
                    name = "rpc response subscription thread $groupName-$topic",
                    priority = -1,
                    block = ::runConsumeLoop
                )
            }
        }
    }


    override fun stop() {
        if (!stopped) {
            val thread = lock.withLock {
                stopped = true
                val threadTmp = consumeLoopThread
                consumeLoopThread = null
                threadTmp
            }
            thread?.join(consumerThreadStopTimeout)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runConsumeLoop() {
        var attempts = 0
        while (!stopped) {
            attempts++
            try {
                log.debug { "Creating rpc response consumer.  Attempt: $attempts" }
                consumerBuilder.createRPCConsumer(
                    config.getConfig(KafkaProperties.KAFKA_CONSUMER),
                    String::class.java,
                    RPCResponse::class.java
                ).use {
                    partitions = it.getPartitions(
                        "$topicPrefix$topic.resp",
                        Duration.ofSeconds(consumerThreadStopTimeout)
                    )
                    it.assign(partitions)
                    pollAndProcessRecords(it)
                }
                attempts = 0
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn("$errorMsg. Attempts: $attempts. Retrying.", ex)
                    }
                    else -> {
                        log.error("$errorMsg. Fatal error occurred. Closing subscription.", ex)
                        stop()
                    }
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun pollAndProcessRecords(consumer: CordaKafkaConsumer<String, RPCResponse>) {
        while (!stopped) {
            val consumerRecords = consumer.poll()
            try {
                processRecords(consumerRecords)
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIFatalException,
                    is CordaMessageAPIIntermittentException -> {
                        throw ex
                    }
                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Failed to process records from topic $topic, group $groupName.", ex
                        )
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun processRecords(consumerRecords: List<ConsumerRecordAndMeta<String, RPCResponse>>) {
        consumerRecords.forEach {
            val correlationKey = it.record.key()
            val future = futureMap[correlationKey]

            when(it.record.value().responseStatus) {
                ResponseStatus.OK -> {
                    val responseBytes = it.record.value().payload
                    val byteArrayInputStream = ByteArrayInputStream(responseBytes.array())
                    val objectInput: ObjectInput
                    objectInput = ObjectInputStream(byteArrayInputStream)
                    val response = objectInput.readObject() as TRESP
                    byteArrayInputStream.close()

                    log.info("Response for request $correlationKey was received at ${Date(it.record.value().sendTime)}")

                    future?.complete(response)
                }
                ResponseStatus.FAILED -> {
                    TODO("throw rpc specific error")
                }
                ResponseStatus.CANCELLED -> {
                    TODO("what happens here")
                }

            }
        }
    }

    override fun sendRequest(req: TREQ): CompletableFuture<TRESP> {
        val uuid = UUID.randomUUID().toString()

        val bytesOut = ByteArrayOutputStream()
        val objectStream = ObjectOutputStream(bytesOut)
        objectStream.writeObject(req)
        objectStream.flush()
        val reqBytes: ByteArray = bytesOut.toByteArray()
        bytesOut.close()
        objectStream.close()

        val request = RPCRequest(
            uuid,
            Instant.now().toEpochMilli(),
            partitions[0].partition(),
            ByteBuffer.wrap(reqBytes)
        )

        val record = Record(rpcConfig.requestTopic, uuid, request)
        publisher.publish(listOf(record))
        val future = CompletableFuture<TRESP>()
        futureMap[uuid] = future

        return future
    }
}