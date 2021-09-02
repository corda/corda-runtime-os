package net.corda.rpc.sender

import net.corda.lifecycle.Lifecycle
import java.util.concurrent.CompletableFuture

interface RPCSender: Lifecycle {

    /**
     * Register a processor for the [RPCSender] to handle all inbound responses
     */
    fun registerProcessor(senderProcessor: RPCSenderProcessor): AutoCloseable

    /**
     * Send request via RPC
     */
    fun sendRequest(key: String, message: String): CompletableFuture<Unit>

}