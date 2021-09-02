package net.corda.messaging.api.rpc.factory

import com.typesafe.config.Config
import net.corda.messaging.api.rpc.responder.RPCResponder
import net.corda.messaging.api.rpc.responder.RPCResponderProcessor
import net.corda.messaging.api.rpc.sender.RPCSender

interface RPCFactory<TREQ, TRESP> {
    /**
     * Create an instance of the [RPCSender]
     * @param subscriptionConfig configuration object used to initialize the subscription
     * @param config other configuration settings if needed
     */
    fun createRPCSender(subscriptionConfig: Config, config: Config): RPCSender<TREQ, TRESP>

    /**
     * Create an instance of the [RPCResponder]
     * @param subscriptionConfig configuration object used to initialize the subscription
     * @param config other configuration settings if needed
     * @param responderProcessor processor in charge of handling incoming requests
     */
    fun createRPCResponder(subscriptionConfig: Config, config: Config,
                           responderProcessor: RPCResponderProcessor<TREQ, TRESP>): RPCResponder<TREQ, TRESP>
}