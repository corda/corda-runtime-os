package net.corda.introspiciere.server

import net.corda.introspiciere.core.KafkaConfig
import net.corda.introspiciere.core.KafkaConfigImpl
import net.corda.introspiciere.core.MessagesGateway
import net.corda.introspiciere.core.MessagesGatewayImpl
import net.corda.introspiciere.core.TopicGateway
import net.corda.introspiciere.core.TopicGatewayImpl

open class DefaultAppContext : AppContext {
    override val kafkaConfig: KafkaConfig
        get() = KafkaConfigImpl()
    override val topicGateway: TopicGateway
        get() = TopicGatewayImpl(kafkaConfig)
    override val messagesGateway: MessagesGateway
        get() = MessagesGatewayImpl(kafkaConfig)
}