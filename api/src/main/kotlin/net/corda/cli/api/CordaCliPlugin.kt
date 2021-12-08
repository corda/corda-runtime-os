package net.corda.cli.api

import net.corda.cli.api.services.HttpRpcService
import org.pf4j.ExtensionPoint

/**
 * CordaCliPlugin is the extension point for plugins.
 */
interface CordaCliPlugin : ExtensionPoint {
    val version: String
    val pluginId: String
    var service: HttpRpcService

    fun setHttpService(httpRpcService: HttpRpcService)
}
