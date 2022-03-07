package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import net.corda.schema.Schemas.Companion.getStateAndEventDLQTopic
import net.corda.schema.Schemas.Companion.getStateAndEventStateTopic
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_STATUS_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_ENTITY_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_GROUP_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_MGMT_REQ_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_MGMT_RESP_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_ROLE_TOPIC
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_USER_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.CPI_INFO_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_INFO_TOPIC
import org.apache.kafka.clients.admin.NewTopic

class CreateTopics(private val context: TaskContext) : Task {

    override fun execute() {
        val compactOption = mapOf("cleanup.policy" to "compact")

        val topics = listOf(
            createTopic(CONFIG_TOPIC, 1, 3, compactOption),

            createTopic(FLOW_EVENT_TOPIC, 4, 3),
            createTopic(FLOW_STATUS_TOPIC , 4, 3, compactOption),
            createTopic(getStateAndEventDLQTopic(FLOW_EVENT_TOPIC), 4, 3),
            createTopic(getStateAndEventStateTopic(FLOW_EVENT_TOPIC) , 4, 3, compactOption),

            createTopic(FLOW_MAPPER_EVENT_TOPIC, 4, 3),
            createTopic(getStateAndEventDLQTopic(FLOW_MAPPER_EVENT_TOPIC), 4, 3),
            createTopic(getStateAndEventStateTopic(FLOW_MAPPER_EVENT_TOPIC), 4, 3, compactOption),

            createTopic(CPI_INFO_TOPIC, 3, 3, compactOption),
            createTopic(VIRTUAL_NODE_INFO_TOPIC, 3, 3, compactOption),

            createTopic(RPC_PERM_MGMT_REQ_TOPIC, 1, 1),
            createTopic(RPC_PERM_MGMT_RESP_TOPIC, 1, 1),
            createTopic(RPC_PERM_USER_TOPIC, 1, 1, compactOption),
            createTopic(RPC_PERM_GROUP_TOPIC, 1, 1, compactOption),
            createTopic(RPC_PERM_ROLE_TOPIC, 1, 1, compactOption),
            createTopic(RPC_PERM_ENTITY_TOPIC, 1, 1, compactOption),
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

