package net.corda.messaging.api.rpc.factory

import com.typesafe.config.Config
import net.corda.messaging.api.rpc.responder.RPCResponder
import net.corda.messaging.api.rpc.sender.RPCSender

interface RPCFactory<TREQ, TRESP> {
    /**
     * Create an instance of the [RPCSender]
     * @param bootstrapConfig configuration object used to bootstrap the service
     */
    fun createRPCSender(bootstrapConfig: Config): RPCSender<TREQ, TRESP>

    /**
     * Create an instance of the [RPCResponder]
     * @param bootstrapConfig configuration object used to bootstrap the service
     */
    fun createRPCResponder(bootstrapConfig: Config): RPCResponder<TREQ, TRESP>
}