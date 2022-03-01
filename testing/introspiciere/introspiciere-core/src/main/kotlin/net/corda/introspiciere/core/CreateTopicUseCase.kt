package net.corda.introspiciere.core

import net.corda.introspiciere.domain.TopicDefinition


/**
 * Use case to create a topic in Kafka.
 */
class CreateTopicUseCase(private val topicGateway: TopicGateway) : UseCase<TopicDefinition> {
    override fun execute(input: TopicDefinition) = topicGateway.create(input)
}
