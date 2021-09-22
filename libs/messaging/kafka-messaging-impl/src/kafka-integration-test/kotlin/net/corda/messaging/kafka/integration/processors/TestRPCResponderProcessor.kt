package net.corda.messaging.kafka.integration.processors

import net.corda.messaging.api.processor.RPCResponderProcessor
import java.util.concurrent.CompletableFuture

class TestRPCResponderProcessor : RPCResponderProcessor<String, String> {

    override fun onNext(request: String, respFuture: CompletableFuture<String>) {
        respFuture.complete("RECEIVED and PROCESSED")
    }

}