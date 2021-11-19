package net.corda.messaging.emulation.subscription.rpc

import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.emulation.rpc.RPCTopicService

class RPCSubscriptionImpl<REQUEST, RESPONSE>(
    private val topicName: String,
    private val rpcTopicService: RPCTopicService,
    private val responderProcessor: RPCResponderProcessor<REQUEST, RESPONSE>
) : RPCSubscription<REQUEST, RESPONSE> {

    private var running = false

    override val isRunning get() = running

    override fun start() {
        running = true
        rpcTopicService.subscribe(topicName,responderProcessor)
    }

    override fun stop() {
        rpcTopicService.unsubscribe(topicName,responderProcessor)
        running = false
    }
}