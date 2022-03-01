package net.corda.introspiciere.server

import net.corda.introspiciere.core.KafkaConfig
import net.corda.introspiciere.core.MessagesGateway
import net.corda.introspiciere.core.TopicGateway

interface AppContext {
    val kafkaConfig: KafkaConfig
    val topicGateway: TopicGateway
    val messagesGateway: MessagesGateway
}