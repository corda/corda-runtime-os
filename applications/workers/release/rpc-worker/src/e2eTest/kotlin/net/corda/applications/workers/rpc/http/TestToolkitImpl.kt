package net.corda.applications.workers.rpc.http

import net.corda.applications.workers.rpc.kafka.KafkaTestToolKit
import net.corda.httprpc.RpcOps
import net.corda.httprpc.client.HttpRpcClient
import net.corda.httprpc.client.config.HttpRpcClientConfig
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

class TestToolkitImpl(
    private val testCaseClass: Class<Any>,
    private val baseAddress: String,
    kafkaServer: String,
) : TestToolkit {

    private val counter = AtomicInteger()

    private val uniqueNamePrefix: String = run {
        if (testCaseClass.simpleName != "Companion") {
            testCaseClass.simpleName
        } else {
            // Converts "net.corda.applications.rpc.LimitedUserAuthorizationE2eTest$Companion"
            // Into: LimitedUserAuthorizationE2eTest
            testCaseClass.name
                .substringBeforeLast('$')
                .substringAfterLast('.')
        }
            .substring(0..15) // Also need to truncate it to avoid DB errors
    }

    /**
     * Good unique name will be:
     * "$testCaseClass-counter-currentTimeMillis"
     * [testCaseClass] will ensure traceability of the call, [counter] will help to avoid clashes within the same
     * testcase run and `currentTimeMillis` will provision for re-runs of the same test without wiping the database.
     */
    override val uniqueName: String
        get() = "$uniqueNamePrefix-${counter.incrementAndGet()}-${System.currentTimeMillis()}"

    override fun <I : RpcOps> httpClientFor(rpcOpsClass: Class<I>, userName: String, password: String): HttpRpcClient<I> {
        return HttpRpcClient(
            baseAddress, rpcOpsClass,
            HttpRpcClientConfig()
                .enableSSL(true)
                .minimumServerProtocolVersion(1)
                .username(userName)
                .password(password)
        )
    }

    private val kafkaToolKit = KafkaTestToolKit(kafkaServer)

    override fun publishRecordsToKafka(records: Collection<Record<*, *>>) {
        if (records.isNotEmpty()) {
            kafkaToolKit.publisherFactory.createPublisher(
                PublisherConfig(uniqueName, false),
                kafkaToolKit.messagingConfiguration,
            ).use { publisher ->
                publisher.publish(records.toList()).forEach {
                    it.join()
                }
            }
        }
    }

    override fun <K : Any, V : Any> acceptRecordsFromKafka(
        topic: String,
        keyClass: Class<K>,
        valueClass: Class<V>,
        block: (Record<K, V>) -> Unit,
    ): AutoCloseable {
        return kafkaToolKit.subscriptionFactory.createPubSubSubscription(
            subscriptionConfig = SubscriptionConfig(
                groupName = uniqueName,
                eventTopic = topic,
            ),
            messagingConfig = kafkaToolKit.messagingConfiguration,
            processor = object : PubSubProcessor<K, V> {
                override fun onNext(event: Record<K, V>): Future<Unit> {
                    block.invoke(event)
                    return CompletableFuture.completedFuture(Unit)
                }

                override val keyClass = keyClass
                override val valueClass = valueClass
            },
        ).also {
            it.start()
        }
    }
}
