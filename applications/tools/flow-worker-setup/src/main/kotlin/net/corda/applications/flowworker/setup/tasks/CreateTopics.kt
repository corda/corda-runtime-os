package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import net.corda.schema.Schemas.Companion.getStateAndEventDLQTopic
import net.corda.schema.Schemas.Companion.getStateAndEventStateTopic
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.CPI_INFO_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import org.apache.kafka.clients.admin.NewTopic

class CreateTopics(private val context: TaskContext) : Task {

    override fun execute() {
        val compactOption = mapOf("cleanup.policy" to "compact")

        val topics = listOf(
            createTopic(CONFIG_TOPIC, 1, 3, compactOption),

            createTopic(FLOW_EVENT_TOPIC, 3, 3),
            createTopic(getStateAndEventDLQTopic(FLOW_EVENT_TOPIC), 3, 3),
            createTopic(getStateAndEventStateTopic(FLOW_EVENT_TOPIC) , 3, 3, compactOption),

            createTopic(FLOW_MAPPER_EVENT_TOPIC, 3, 3),
            createTopic(getStateAndEventDLQTopic(FLOW_MAPPER_EVENT_TOPIC), 3, 3),
            createTopic(getStateAndEventStateTopic(FLOW_MAPPER_EVENT_TOPIC), 3, 3, compactOption),

            createTopic(CPI_INFO_TOPIC, 3, 3, compactOption),
            createTopic(VIRTUAL_NODE_INFO_TOPIC, 3, 3, compactOption),
        )

        context.createTopics(topics)

    }

    private fun createTopic(
        name: String,
        partitions: Int,
        repFactor: Short,
        config: Map<String, String> = mapOf()
    ): NewTopic {
        val topic = NewTopic(name, partitions, repFactor)
        topic.configs(config)
        return topic
    }
}

