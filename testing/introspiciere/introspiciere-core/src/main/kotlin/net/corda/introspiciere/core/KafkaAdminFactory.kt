package net.corda.introspiciere.core

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig

class KafkaAdminFactory(private val servers: List<String>) {
    fun create(): Admin = mapOf(
        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to servers.joinToString(",")
    ).toProperties().let(Admin::create)
}