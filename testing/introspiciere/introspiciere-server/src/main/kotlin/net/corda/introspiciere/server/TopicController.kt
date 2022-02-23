package net.corda.introspiciere.server

import io.javalin.http.Handler
import net.corda.introspiciere.core.CreateTopicUseCase
import net.corda.introspiciere.core.KafkaConfig
import net.corda.introspiciere.core.SimpleKafkaClient

internal class TopicController(private val kafkaConfig: KafkaConfig) {
    fun getAll(): Handler = Handler { ctx ->
        wrapException {
            val topics = SimpleKafkaClient(listOf(kafkaConfig.brokers)).fetchTopics()
            ctx.result(topics)
        }
    }

    fun create(): Handler = Handler {
        wrapException {
            CreateTopicUseCase(kafkaConfig)
        }
    }
}