package net.corda.configuration.write

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

// TODO - Joel - Look at document around config audit table (probs do both inserts in same tx):
//  https://github.com/corda/platform-eng-design/blob/15449c129370bc4414a6fb6d20e0832d691a4f73/core/corda-5/corda-5.1/rpc-apis/rpc_api.md#cluster-database
// TODO - Joel - Try and write the RPC gateway as well, for full e2e example.

/** Wraps a `PersistentConfigWriter` to persist and broadcast cluster configuration updates. */
interface ConfigWriteService : Lifecycle {

    /**
     * Starts processing cluster configuration updates.
     *
     * @param config Config to be used by the subscription.
     * @param instanceId The instance ID to use for subscribing to Kafka.
     */
    fun startProcessing(config: SmartConfig, instanceId: Int)
}