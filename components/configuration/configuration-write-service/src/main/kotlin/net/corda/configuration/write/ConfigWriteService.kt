package net.corda.configuration.write

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

// TODO - Joel - Hoist into `components` module.
// TODO - Joel - Look at document around config audit table (probs do both inserts in same tx):
//  https://github.com/corda/platform-eng-design/blob/15449c129370bc4414a6fb6d20e0832d691a4f73/core/corda-5/corda-5.1/rpc-apis/rpc_api.md#cluster-database
// TODO - Joel - Try and write the RPC gateway as well, for full e2e example.

/**
 * Receives RPC requests to update the cluster's config, updates the config in the cluster database, and publishes the
 * updated config for use by the rest of the cluster.
 */
interface ConfigWriteService : Lifecycle {

    /** Bootstraps the [ConfigWriteService] by providing the required information to connect to Kafka. */
    fun bootstrapConfig(config: SmartConfig, instanceId: Int)
}