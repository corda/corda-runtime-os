package net.corda.messagebus.kafka.admin

import net.corda.messagebus.api.admin.Admin
import org.apache.kafka.clients.admin.AdminClient

class KafkaAdmin(private val adminClient: AdminClient) : Admin {
    override fun getTopics(): Set<String> {
        return adminClient.listTopics().names().get()
    }

    override fun close() {
        adminClient.close()
    }
}
