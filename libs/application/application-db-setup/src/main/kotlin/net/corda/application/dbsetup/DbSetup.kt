package net.corda.application.dbsetup

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory

/**
 * The implementation of this interface is called during the Corda runtime
 * set up.
 */
interface DbSetup {
    fun run(config: SmartConfig, configFactory: SmartConfigFactory)
}
