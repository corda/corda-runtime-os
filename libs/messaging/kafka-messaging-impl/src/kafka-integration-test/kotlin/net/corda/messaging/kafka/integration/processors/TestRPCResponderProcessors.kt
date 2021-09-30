package net.corda.messaging.kafka.integration.processors

import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.processor.RPCResponderProcessor
import java.util.concurrent.CompletableFuture

class TestRPCResponderProcessor : RPCResponderProcessor<String, String> {
    override fun onNext(request: String, respFuture: CompletableFuture<String>) {
        respFuture.complete("RECEIVED and PROCESSED")
    }
}

class TestRPCErrorResponderProcessor: RPCResponderProcessor<String, String> {
    override fun onNext(request: String, respFuture: CompletableFuture<String>) {
        respFuture.completeExceptionally(CordaRPCAPIResponderException("Responder exception"))
    }
}

class TestRPCCancelResponderProcessor: RPCResponderProcessor<String, String> {
    override fun onNext(request: String, respFuture: CompletableFuture<String>) {
        respFuture.cancel(true)
    }
}