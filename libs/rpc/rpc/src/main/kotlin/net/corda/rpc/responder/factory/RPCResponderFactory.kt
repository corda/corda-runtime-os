package net.corda.rpc.responder.factory

import com.typesafe.config.Config
import net.corda.rpc.responder.RPCResponder

interface RPCResponderFactory {

    /**
     * Create an instance of the [RPCResponder]
     * @param bootstrapConfig configuration object used to bootstrap the service
     */
    fun createRPCResponder(bootstrapConfig: Config): RPCResponder
}