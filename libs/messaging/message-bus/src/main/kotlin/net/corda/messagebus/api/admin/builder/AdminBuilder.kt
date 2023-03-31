package net.corda.messagebus.api.admin.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.admin.Admin
import net.corda.messagebus.api.configuration.AdminConfig

/**
 * Builder Interface for creating instances of [Admin].
 */
interface AdminBuilder {

    /**
     * Generate a message bus admin with given properties.
     * @param adminConfig The mandatory config for setting up admin
     * @param messageBusConfig Configuration for connecting to the message bus and controlling its behaviour.
     * @return A new instance of [Admin] for the underlying bus implementation .
     * @throws CordaMessageAPIFatalException thrown if an instance of [Admin] cannot be created.
     */
    fun createAdmin(
        adminConfig: AdminConfig,
        messageBusConfig: SmartConfig
    ): Admin
}
