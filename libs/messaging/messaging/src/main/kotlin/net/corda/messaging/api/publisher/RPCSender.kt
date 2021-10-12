package net.corda.messaging.api.publisher

import net.corda.lifecycle.Lifecycle
import java.util.concurrent.CompletableFuture


/**
 * Interface for posting requests of type [TREQ] and receiving responses of type [TRESP]
 * RPCSender instances can be created via the [PublisherFactory].
 *
 * The sender contains a subscription for reading back responses. Depending on the response status, the future will be
 * completed normally, exceptionally or be cancelled.
 * Clients should expect the future to throw a CordaRPCAPIResponderException if it was completed exceptionally or a
 * CancellationException if it was cancelled when calling get()/getOrThrow() on future
 *
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
     * @throws CordaRPCAPISenderException if there are no partitions available when attempting to send or if any errors
     * occur during the publishing process
     *
     * The client is responsible for retries in the event of a failure or timeout
     */
    fun sendRequest(req: TREQ): CompletableFuture<TRESP>

}