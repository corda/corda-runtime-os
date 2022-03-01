package net.corda.introspiciere.junit

import net.corda.introspiciere.core.KafkaConfig
import net.corda.introspiciere.core.MessagesGateway
import net.corda.introspiciere.core.TopicGateway
import net.corda.introspiciere.server.AppContext

class FakeAppContext : AppContext {
    override val kafkaConfig: KafkaConfig
        get() = TODO("Not yet implemented")
    override val topicGateway: TopicGateway = FakeTopicGateway()
    override val messagesGateway: MessagesGateway = FakeMessageGateway()
}