package net.corda.messaging.emulation.rpc

import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.processor.RPCResponderProcessor
import org.osgi.service.component.annotations.Component
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Component(service = [RPCTopicService::class])
class RPCTopicServiceImpl(
    private var executorService: ExecutorService = Executors.newCachedThreadPool()
) : RPCTopicService {
    private val topics = mutableMapOf<String, RPCProducerConsumerLink<*, *>>()

    override fun <REQUEST, RESPONSE> subscribe(topic: String, consumer: RPCResponderProcessor<REQUEST, RESPONSE>) {
        @Suppress("unchecked_cast")
        val link: RPCProducerConsumerLink<REQUEST, RESPONSE> = topics.computeIfAbsent(topic) {
            RPCProducerConsumerLink<REQUEST, RESPONSE>()
        } as RPCProducerConsumerLink<REQUEST, RESPONSE>
        link.addConsumer(consumer)
    }

    override fun <REQUEST, RESPONSE> unsubscribe(topic: String, consumer: RPCResponderProcessor<REQUEST, RESPONSE>) {
        if (!topics.containsKey(topic)) {
            return
        } else {
            @Suppress("unchecked_cast")
            val link: RPCProducerConsumerLink<REQUEST, RESPONSE> = topics[topic] as RPCProducerConsumerLink<REQUEST, RESPONSE>
            link.removeConsumer(consumer)

            if (link.consumerList.isEmpty()) {
                topics.remove(topic)
            }
        }
    }

    override fun <REQUEST, RESPONSE> publish(
        topic: String,
        request: REQUEST,
        requestCompletion: CompletableFuture<RESPONSE>
    ) {
        if (!topics.containsKey(topic)) {
            return
        }

        @Suppress("unchecked_cast")
        val link: RPCProducerConsumerLink<REQUEST, RESPONSE> = topics[topic] as RPCProducerConsumerLink<REQUEST, RESPONSE>

        executorService.submit {
            link.handleRequest(request, requestCompletion)
        }
    }

    class RPCProducerConsumerLink<REQUEST, RESPONSE> {
        var consumerList  = mutableListOf<RPCResponderProcessor<REQUEST, RESPONSE>>()
        private var currentConsumer: Int = 0

        fun addConsumer(consumer: RPCResponderProcessor<REQUEST, RESPONSE>) {
            synchronized(consumerList) {
                consumerList.add(consumer)
            }
        }

        fun removeConsumer(consumer: RPCResponderProcessor<REQUEST, RESPONSE>) {
            synchronized(consumerList) {
                consumerList.remove(consumer)
            }
        }

        fun handleRequest(request: REQUEST, requestCompletion: CompletableFuture<RESPONSE>) {
            val responseCompletion = CompletableFuture<RESPONSE>().also {
                it.whenComplete { response, error ->
                    when {
                        it.isCancelled -> {
                            requestCompletion.completeExceptionally(
                                CordaRPCAPIResponderException(
                                    CancellationException::class.java.name,
                                    "The request was cancelled by the responder."
                                )
                            )
                        }

                        it.isCompletedExceptionally -> {
                            requestCompletion.completeExceptionally(
                                CordaRPCAPIResponderException(
                                    error.javaClass.name,
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
                synchronized(consumerList) {
                    val consumerToUse = currentConsumer
                    currentConsumer++
                    if (currentConsumer >= consumerList.size) {
                        currentConsumer = 0
                    }
                    consumerList[consumerToUse]
                }.onNext(request, responseCompletion)
            } catch (e: Throwable) {
                responseCompletion.completeExceptionally(
                    CordaRPCAPIResponderException(
                        e.javaClass.name,
                        "The responder failed to process the request.",
                        e
                    )
                )
            }
        }
    }
}


