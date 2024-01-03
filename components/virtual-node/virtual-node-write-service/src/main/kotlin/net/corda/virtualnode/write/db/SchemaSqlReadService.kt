package net.corda.virtualnode.write.db

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

interface SchemaSqlReadService : Lifecycle {

    fun initialise(config: SmartConfig)

    fun getSchemaSql(dbType: String, virtualNodeShortId: String? = null, cpiChecksum: String? = null): String
}
