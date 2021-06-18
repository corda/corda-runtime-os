package net.corda.libs.configuration.read

import java.util.*

class ConfigListenerSubscription(private val configReadService: ConfigReadService, private val id: UUID) : AutoCloseable {
    override fun close() {
        configReadService.unregisterCallback(id)
    }
}