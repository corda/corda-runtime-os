package net.corda.p2p.app.topic.dump

import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigFactoryFactory
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import org.apache.avro.generic.IndexedRecord
import org.osgi.framework.FrameworkUtil
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.Closeable
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.random.Random

@Command(
    header = ["Topic dumper"],
    name = "dump-topic",
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    showAtFileInUsageHelp = true,
)
internal class TopicDumper(
    private val subscriptionFactory: SubscriptionFactory,
    private val configMerger: ConfigMerger
) : Runnable, Closeable {
    companion object {
        private val logger = LoggerFactory.getLogger("Console")
    }
    @Option(
        names = ["-m", "--messaging-params"],
        description = ["Messaging parameters for the topic dumper."]
    )
    var messagingParams = emptyMap<String, String>()

    @Option(
        names = ["-t", "--topic"],
        description = ["The name of the topic to dump."],
        required = true,
    )
    private lateinit var topic: String

    @Option(
        names = ["-v", "--values"],
        description = ["The class name of the expected value (full canonical name)."],
        required = true,
    )
    private lateinit var values: String

    @Option(
        names = ["-o", "--output"],
        description = ["The output file."],
        required = true,
    )
    private lateinit var output: File

    private val count = AtomicLong(0)

    private var subscription: AutoCloseable? = null

    private val objectMapper = ObjectMapper()

    private fun toDisplayableMap(value: Any?): Any? {
        return when (value) {
            null, is Number, is Boolean, is String -> value
            is Map<*, *> -> {
                value.mapValues {
                    toDisplayableMap(it.value)
                }.mapKeys {
                    it.key?.toString() ?: "null"
                }
            }
            is Collection<*> -> {
                value.map { toDisplayableMap(it) }
            }
            is Array<*> -> {
                value.map { toDisplayableMap(it) }
            }
            is IndexedRecord -> {
                mapOf(
                    "type" to value.javaClass.name,
                    "content" to value.schema.fields.associate { field ->
                        field.name() to toDisplayableMap(value.get(field.pos()))
                    }

                )
            }
            else -> value.toString()
        }
    }

    private fun <T : Any> createProcessor(): DurableProcessor<String, T> {
        val clz = FrameworkUtil.getBundle(this.javaClass).bundleContext.bundles.mapNotNull { bundle ->
            try {
                @Suppress("UNCHECKED_CAST")
                bundle.loadClass(values) as Class<T>
            } catch (e: ClassNotFoundException) {
                null
            }
        }.firstOrNull()
            ?: throw Application.TopicDumperException("Could not find Avro type for class $values.")

        return object : DurableProcessor<String, T> {
            override fun onNext(events: List<Record<String, T>>): List<Record<*, *>> {
                events.forEach {
                    val valueAsMap = toDisplayableMap(it.value)
                    val valueAsString = objectMapper.writeValueAsString(valueAsMap)
                    output.appendText("${it.key}| $valueAsString\n")
                    count.incrementAndGet()
                }
                return emptyList()
            }

            override val keyClass = String::class.java
            override val valueClass = clz
        }
    }

    private fun reportStatus() {
        thread(isDaemon = true) {
            while (true) {
                Thread.sleep(1000)
                logger.info("Got $count messages")
            }
        }
    }

    override fun run() {
        output.parentFile.mkdirs()
        val subscriptionConfig = SubscriptionConfig("topic-dumper-${UUID.randomUUID()}", topic)
        val parsedMessagingParams = messagingParams.mapKeys { (key, _) ->
            "${BootConfig.BOOT_KAFKA_COMMON}.${key.trim()}"
        }.toMutableMap()
        parsedMessagingParams.computeIfAbsent("${BootConfig.BOOT_KAFKA_COMMON}.bootstrap.servers") {
            "localhost:9092"
        }
        logger.info("Connecting to ${parsedMessagingParams["${BootConfig.BOOT_KAFKA_COMMON}.bootstrap.servers"]}")
        val kafkaConfig = SmartConfigFactoryFactory.createWithoutSecurityServices().create(
            ConfigFactory.parseMap(parsedMessagingParams)
                .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("KAFKA"))
                .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
                .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(Random.nextInt()))
        )
        subscription = subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            createProcessor(),
            configMerger.getMessagingConfig(kafkaConfig),
            null
        ).also {
            it.start()
        }
        logger.info("Started dumping $topic into $output")
        reportStatus()
    }

    override fun close() {
        subscription?.close()
    }
}
