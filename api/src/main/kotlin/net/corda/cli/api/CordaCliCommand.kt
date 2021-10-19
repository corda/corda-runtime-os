package net.corda.cli.api

import org.pf4j.ExtensionPoint

/**
 * CordaCliCommand is the extension point for plugins.
 */
interface CordaCliCommand : ExtensionPoint {
    // placeholder, as design progresses we may need more interface var or methods
    val pluginID: String
        get() = "plugin1"
}
