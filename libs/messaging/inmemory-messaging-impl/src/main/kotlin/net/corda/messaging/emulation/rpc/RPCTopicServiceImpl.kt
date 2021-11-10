package net.corda.messaging.emulation.rpc

import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.processor.RPCResponderProcessor
import org.osgi.service.component.annotations.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Component(service = [RPCTopicService::class])
class RPCTopicServiceImpl(
    private var executorService: ExecutorService = Executors.newCachedThreadPool()
) : RPCTopicService {
    private val topics = mutableMapOf<String, RPCProducerConsumerLink<Any, Any>>()

    @Suppress("UNCHECKED_CAST")
    override fun <REQUEST, RESPONSE> subscribe(topic: String, consumer: RPCResponderProcessor<REQUEST, RESPONSE>) {
        if (!topics.containsKey(topic)) {
            topics[topic] = RPCProducerConsumerLink(consumer) as RPCProducerConsumerLink<Any, Any>
        } else {
            (topics[topic] as RPCProducerConsumerLink<REQUEST, RESPONSE>).addConsumer(consumer)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <REQUEST, RESPONSE> unsubscribe(topic: String, consumer: RPCResponderProcessor<REQUEST, RESPONSE>) {
        if (!topics.containsKey(topic)) {
            return
        } else {
            val consumerLink = topics[topic] as RPCProducerConsumerLink<REQUEST, RESPONSE>
            consumerLink.removeConsumer(consumer)
            if (consumerLink.consumerList.isEmpty()) {
                topics.remove(topic)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <REQUEST, RESPONSE> publish(
        topic: String,
        request: REQUEST,
        requestCompletion: CompletableFuture<RESPONSE>
    ) {
        if (!topics.containsKey(topic)) {
            return
        }

        val producerConsumerLink = topics[topic]!! as RPCProducerConsumerLink<REQUEST, RESPONSE>

        executorService.submit {
            producerConsumerLink.handleRequest(request, requestCompletion)
        }
    }

    class RPCProducerConsumerLink<REQUEST, RESPONSE>(
        consumer: RPCResponderProcessor<REQUEST, RESPONSE>
    ) {
        var consumerList: List<RPCResponderProcessor<REQUEST, RESPONSE>> = listOf(consumer)
        private var currentConsumer: Int = 0

        fun addConsumer(consumer: RPCResponderProcessor<REQUEST, RESPONSE>) {
            consumerList += consumer
        }

        fun removeConsumer(consumer: RPCResponderProcessor<REQUEST, RESPONSE>) {
            consumerList -= consumer
        }

        fun handleRequest(request: REQUEST, requestCompletion: CompletableFuture<RESPONSE>) {
            val responseCompletion = CompletableFuture<RESPONSE>().also {
                it.whenComplete { response, error ->
                    when {
                        it.isCancelled -> {
                            requestCompletion.completeExceptionally(CordaRPCAPIResponderException("The request was cancelled by the responder."))
                        }

                        it.isCompletedExceptionally -> {
                            requestCompletion.completeExceptionally(
                                CordaRPCAPIResponderException(
                                    "The responder failed to process the request.",
                                    error
                                )
                            )
                        }
                        else -> {
                            requestCompletion.complete(response)
                        }
                    }
                }
            }

            /* A simple round-robin dispatch of requests to handlers is used to allow for scenarios where multiple
             * consumers are used for a given topic
             */
            try {
                consumerList[currentConsumer++].onNext(request, responseCompletion)
            } catch (e: Throwable) {
                responseCompletion.completeExceptionally(
                    CordaRPCAPIResponderException(
                        "The responder failed to process the request.",
                        e
                    )
                )
            }

            if (currentConsumer >= consumerList.size) {
                currentConsumer = 0
            }
        }
    }
}


