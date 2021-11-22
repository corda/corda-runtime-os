package net.corda.messaging.emulation.publisher

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.emulation.rpc.RPCTopicService
import java.util.concurrent.CompletableFuture

class RPCSenderImpl<REQUEST, RESPONSE>(
    private val topicName: String,
    private val rpcTopicService: RPCTopicService) : RPCSender<REQUEST, RESPONSE> {

    private var running = false

    override val isRunning get() = running

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun sendRequest(req: REQUEST): CompletableFuture<RESPONSE> {
        if(!running){
            throw CordaRPCAPISenderException("The sender has not been started")
        }
        return CompletableFuture<RESPONSE>().also {
            rpcTopicService.publish(topicName, req, it)
        }
    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName("RPCSender")
}