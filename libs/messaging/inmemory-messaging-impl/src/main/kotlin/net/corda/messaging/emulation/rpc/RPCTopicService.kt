package net.corda.messaging.emulation.rpc

import net.corda.messaging.api.processor.RPCResponderProcessor
import java.util.concurrent.CompletableFuture

interface RPCTopicService {

    /**
     * Add a Responder Processor subscription to a given topic
     */
    fun <REQUEST, RESPONSE> subscribe(topic: String, consumer: RPCResponderProcessor<REQUEST, RESPONSE>)

    /**
     * Remove a Responder Processor subscription to a given topic
     */
    fun <REQUEST, RESPONSE> unsubscribe(topic: String, consumer: RPCResponderProcessor<REQUEST, RESPONSE>)

    /**
     * Publish a request to a named topic and link the request future to the response.
     */
    fun <REQUEST, RESPONSE> publish(
        topic: String,
        request: REQUEST,
        requestCompletion: CompletableFuture<RESPONSE>
    )
}