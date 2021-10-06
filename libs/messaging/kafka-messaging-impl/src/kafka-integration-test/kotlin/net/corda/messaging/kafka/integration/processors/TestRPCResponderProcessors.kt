package net.corda.messaging.kafka.integration.processors

import net.corda.messaging.api.processor.RPCResponderProcessor
import java.lang.Exception
import java.util.concurrent.CompletableFuture

class TestRPCResponderProcessor : RPCResponderProcessor<String, String> {
    override fun onNext(request: String, respFuture: CompletableFuture<String>) {
        respFuture.complete("RECEIVED and PROCESSED")
    }
}

class TestRPCErrorResponderProcessor: RPCResponderProcessor<String, String> {
    override fun onNext(request: String, respFuture: CompletableFuture<String>) {
        respFuture.completeExceptionally(ArbitraryException("Responder exception"))
    }
}

class TestRPCCancelResponderProcessor: RPCResponderProcessor<String, String> {
    override fun onNext(request: String, respFuture: CompletableFuture<String>) {
        respFuture.cancel(true)
    }
}

class TestRPCUnresponsiveResponderProcessor : RPCResponderProcessor<String, String> {
    override fun onNext(request: String, respFuture: CompletableFuture<String>) {
    }
}

private class ArbitraryException(message: String?, exception: Exception? = null): Exception(message, exception)