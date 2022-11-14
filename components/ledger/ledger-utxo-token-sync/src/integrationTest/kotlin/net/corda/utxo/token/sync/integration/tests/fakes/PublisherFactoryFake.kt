package net.corda.utxo.token.sync.integration.tests.fakes

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.RPCConfig
import java.util.concurrent.CompletableFuture

class PublisherFactoryFake(val onRecordsPublished: (records: List<Record<*, *>>) -> Unit) : PublisherFactory {

    override fun createPublisher(publisherConfig: PublisherConfig, messagingConfig: SmartConfig): Publisher {
        return FakePublisher(onRecordsPublished)

    }

    override fun <REQUEST : Any, RESPONSE : Any> createRPCSender(
        rpcConfig: RPCConfig<REQUEST, RESPONSE>,
        messagingConfig: SmartConfig
    ): RPCSender<REQUEST, RESPONSE> {
        TODO("Not yet implemented")
    }

    private class FakePublisher(val onRecordsPublished: (records: List<Record<*, *>>) -> Unit) : Publisher {

        override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
            onRecordsPublished(records.map { it.second })
            return listOf()
        }

        override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            onRecordsPublished(records)
            return listOf()
        }

        override fun close() {
        }
    }
}