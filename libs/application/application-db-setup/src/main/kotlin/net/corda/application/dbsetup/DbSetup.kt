package net.corda.application.dbsetup

import net.corda.libs.configuration.SmartConfig

/**
 * The implementation of this interface is called during the Corda runtime
 * set up.
 */
interface DbSetup {
    fun run(config: SmartConfig)
}
