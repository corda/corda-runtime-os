package net.corda.introspiciere.server

import io.javalin.http.Handler
import net.corda.introspiciere.core.CreateTopicUseCase
import net.corda.introspiciere.core.KafkaConfig

internal class TopicController(private val kafkaConfig: KafkaConfig) {
    fun create(): Handler = Handler {
        wrapException {
            CreateTopicUseCase(kafkaConfig)
        }
    }
}