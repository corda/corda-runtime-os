package net.corda.introspiciere.core

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG
import java.util.*

class SimpleKafkaClient(val servers: List<String>) {

    fun fetchTopics(): String {
        val properties = Properties()
        properties[BOOTSTRAP_SERVERS_CONFIG] = servers.joinToString(",")
        //properties[AdminClientConfig.RETRIES_CONFIG] = 5//Int.MAX_VALUE
        //properties[AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = 20000 //Must be equal or bigger than REQUEST_TIMEOUT_MS_CONFIG
        //properties[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = 5000

        val topicNamesList = Admin.create(properties).use {
            it.listTopics().names().get()
        }

        // TODO make this json
        val topicNames = topicNamesList.joinToString()

        println("Topics: $topicNames")
        return topicNames
    }
}