package net.corda.introspiciere.core

import net.corda.introspiciere.domain.TopicDefinition


/**
 * Use case to create a topic in Kafka.
 */
class CreateTopicUseCase(private val kafkaAdminGateway: KafkaAdminGateway) : UseCase<TopicDefinition> {
    override fun execute(input: TopicDefinition) = kafkaAdminGateway.createTopic(input)
}
