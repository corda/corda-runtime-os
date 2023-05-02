package net.corda.messaging.api.publisher

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.Resource
import java.util.concurrent.CompletableFuture


/**
 * Interface for posting requests of type [REQUEST] and hopefully receiving responses of type [RESPONSE]
 * RPCSender instances can be created via the [PublisherFactory].
 *
 * The sender contains a subscription for reading back responses. Depending on the response status, the future will be
 * completed normally, exceptionally or be cancelled.
 * Clients should expect the future to throw a CordaRPCAPIResponderException if it was completed exceptionally or a
 * CancellationException if it was cancelled when calling get()/getOrThrow() on future
 *
 */
interface RPCSender<REQUEST, RESPONSE> : Resource {

    /**
     * The name of the lifecycle coordinator inside the subscription. You can register a different coordinator to listen
     * for status changes from this subscription by calling [followStatusChangesByName] and passing in this value.
     */
    val subscriptionName: LifecycleCoordinatorName

    fun start()

    /**
     * Send request via RPC
     * @param req of type [REQUEST]
     * @return completable future of type [RESPONSE]
     *
     * The future represents the promise of a response and is completed when said response is received
     * It may error if the response fails or if the request times out. Network RPCs are inherently unreliable,
     * so the caller must be able to recover if this requested is not completed. In particular, if this request
     * goes out via Kafka, we'll include one of the partition numbers this process is listening on for responses
     * in the request, and it may well be that Kafka rebalances the receive topic and the response goes to another
     * consumer within the same group. This can happen without anything failing, and is expected to happen often
     * enough on a Kubernetes deployment that it is not acceptable for the caller to give up and fail in such cases.
     *
     * @throws CordaRPCAPISenderException if there are no partitions available when attempting to send or if any errors
     * occur during the publishing process
     *
     * The client is responsible for retries in the event of a failure or timeout
     */
    fun sendRequest(req: REQUEST): CompletableFuture<RESPONSE>

}
