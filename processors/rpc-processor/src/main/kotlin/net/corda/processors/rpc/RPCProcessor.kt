package net.corda.processors.rpc

import net.corda.libs.configuration.SmartConfig

/** The processor for a `RPCWorker`. */
interface RPCProcessor {
    fun start(instanceId: Int, topicPrefix: String, config: SmartConfig)

    fun stop()
}