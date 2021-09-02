package net.corda.messaging.api.publisher

import net.corda.lifecycle.Lifecycle
import java.util.concurrent.CompletableFuture

interface RPCSender<TREQ, TRESP>: Lifecycle {

    /**
     * Send request via RPC
     */
    fun sendRequest(req: TREQ): CompletableFuture<TRESP>

}