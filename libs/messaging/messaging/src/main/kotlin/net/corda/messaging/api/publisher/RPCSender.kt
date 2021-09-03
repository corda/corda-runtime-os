package net.corda.messaging.api.publisher

import net.corda.lifecycle.Lifecycle
import java.util.concurrent.CompletableFuture


/**
 * Interface for posting requests of type [TREQ] and receiving responses of type [TRESP]
 * RPCSender instances can be created via the [PublisherFactory].
 */
interface RPCSender<TREQ, TRESP>: Lifecycle {

    /**
     * Send request via RPC
     * @param req of type [TREQ]
     * @return completable future of type [TRESP]
     *
     * The future represents the promise of a response and is completed when said response is received
     * It may error if the response fails or if the request times out
     *
     * The client is responsible for retries in the event of a failure or timeout
     */
    fun sendRequest(req: TREQ): CompletableFuture<TRESP>

}