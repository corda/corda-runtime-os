package net.corda.messaging.integration.processors

import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import net.corda.data.messaging.RPCRequest
import net.corda.data.messaging.RPCResponse
import net.corda.data.messaging.ResponseStatus
import net.corda.messaging.api.processor.RPCResponderProcessor

class TestRPCResponderProcessor : RPCResponderProcessor<String, String> {
    override fun onNext(request: String, respFuture: CompletableFuture<String>) {
        respFuture.complete("RECEIVED and PROCESSED")
    }
}

class TestRPCAvroResponderProcessor(val time: Instant) : RPCResponderProcessor<RPCRequest, RPCResponse> {

    override fun onNext(request: RPCRequest, respFuture: CompletableFuture<RPCResponse>) {
        respFuture.complete(RPCResponse("sender", "test", time, ResponseStatus.OK, ByteBuffer.wrap("test".encodeToByteArray())))
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
        if (request == "PLEASE RESPOND") {
            respFuture.complete("RECEIVED and PROCESSED")
        }
    }
}

private class ArbitraryException(message: String?, exception: Exception? = null): Exception(message, exception)
