package net.corda.testing.fake.kafka.runner

import io.github.embeddedkafka.EmbeddedKafka
import io.github.embeddedkafka.EmbeddedKafkaConfig
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import scala.Predef

@Command(
    name = "embedded-kafka",
    mixinStandardHelpOptions = true,
    description = ["Run Kafka in memory. To be used for testing only."],
    showDefaultValues = true,
    showAtFileInUsageHelp= true,
)
internal class KafkaRunner : Runnable {
    @Option(
        names = ["--kafka-port", "-k"],
        description = ["Set the Kafka port"]
    )
    var kafkaPort: Int = 9092

    @Option(
        names = ["--zookeeper-port", "--zoo-keeper-port", "-z"],
        description = ["Set the ZooKeeper port"]
    )
    var zookeeperPort: Int = 2181

    @Option(
        names = ["--skip-topics-creation", "--skip-topics", "-s"],
        description = ["Skip the Corda topic creation"]
    )
    var skipTopicCreation: Boolean = false

    private val config by lazy {
        EmbeddedKafkaConfig.apply(
            kafkaPort,
            zookeeperPort,
            Predef.Map().empty(),
            Predef.Map().empty(),
            Predef.Map().empty(),
        )
    }

    override fun run() {
        println("Running Kafka on port $kafkaPort with zookeeper on port $zookeeperPort")
        EmbeddedKafka.start(config)
        println("Kafka is running")
        if (!skipTopicCreation) {
            TopicsCreator(this).run()
        }
        while (EmbeddedKafka.isRunning()) {
            println("Kafka is still running on port $kafkaPort with zookeeper on port $zookeeperPort")
            Thread.sleep(1000 * 60)
        }
    }
}