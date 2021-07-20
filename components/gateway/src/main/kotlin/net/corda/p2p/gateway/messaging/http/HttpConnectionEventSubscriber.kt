package net.corda.p2p.gateway.messaging.http

import org.slf4j.LoggerFactory
import java.util.concurrent.Flow

class HttpConnectionEventSubscriber : Flow.Subscriber<ConnectionChangeEvent> {

    companion object {
        val logger = LoggerFactory.getLogger(HttpConnectionEventSubscriber::class.java)
    }
    var subscription: Flow.Subscription? = null

    override fun onSubscribe(subscription: Flow.Subscription?) {
        this.subscription = subscription
        subscription?.request(1)
    }

    override fun onNext(item: ConnectionChangeEvent) {
        logger.info("$item")
        subscription?.request(1)
    }

    override fun onError(throwable: Throwable?) {
        logger.error(throwable.toString())
    }

    override fun onComplete() {
        logger.warn("NO MORE SHIT TO CONSUME")
    }
}