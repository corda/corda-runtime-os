package net.corda.testing.fake.kafka.runner

import net.corda.cli.plugins.topicconfig.Create
import net.corda.cli.plugins.topicconfig.CreateConnect
import net.corda.cli.plugins.topicconfig.TopicPlugin

internal class TopicsCreator(
    kafkaRunner: KafkaRunner,
): Runnable {
    private val command by lazy {
        val topic = TopicPlugin.createTopic("localhost:${kafkaRunner.kafkaPort}")
        val create = Create()
        create.topic = topic
        CreateConnect().also {
            it.create = create
        }
    }
    override fun run() {
        println("Creating topics...")
        command.run()
        println("Topic created")
    }

}