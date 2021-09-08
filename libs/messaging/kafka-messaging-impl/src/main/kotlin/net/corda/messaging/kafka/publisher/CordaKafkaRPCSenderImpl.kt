package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.messaging.kafka.subscription.KafkaRPCSubscription
import net.corda.messaging.kafka.utils.render
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CordaKafkaRPCSenderImpl<TREQ : Any, TRESP : Any>(
    private val rpcConfig: RPCConfig<TREQ, TRESP>,
    private val config: Config,
    private val publisher: Publisher,
    private val subscription: KafkaRPCSubscription<TREQ, TRESP>
) : RPCSender<TREQ, TRESP>, RPCSubscription<TREQ, TRESP> {

    private companion object {
        private val log: Logger = contextLogger()
    }

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()

    override val isRunning: Boolean
        get() = !stopped

    override fun start() {
        log.debug { "Starting subscription with config:\n${config.render()}" }
        lock.withLock {
            stopped = false
            subscription.start()
        }
    }

    override fun stop() {
        if (!stopped) {
            lock.withLock {
                stopped = true
                subscription.stop()
            }
        }
    }

    override fun sendRequest(req: TREQ): CompletableFuture<TRESP> {
        val record = Record(rpcConfig.requestTopic, req, null)
        publisher.publishToPartition(listOf(subscription.getSubscriptionPartitions()[0].partition() to record))
        return CompletableFuture<TRESP>()
    }

}