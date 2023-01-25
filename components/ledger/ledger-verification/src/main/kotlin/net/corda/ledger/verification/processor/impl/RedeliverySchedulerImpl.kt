package net.corda.ledger.verification.processor.impl

import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequestRedelivery
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Component(service = [RedeliveryScheduler::class])
class RedeliverySchedulerImpl constructor(
    private val publisherFactory: PublisherFactory,
    private val scheduledExecutorService: ScheduledExecutorService
) : RedeliveryScheduler {

    @Activate
    constructor(
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory
    ) : this(publisherFactory, Executors.newSingleThreadScheduledExecutor())

    private val redeliveries = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private var publisher: Publisher? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        publisher?.close()
        publisher = publisherFactory.createPublisher(
            PublisherConfig("ContractVerificationRequestRedelivery"), config.getConfig(MESSAGING_CONFIG)
        )
    }

    override fun onPartitionSynced(states: Map<String, VerifyContractsRequestRedelivery>) {
        scheduleRedeliveries(states.values)
    }

    override fun onPartitionLost(states: Map<String, VerifyContractsRequestRedelivery>) {
        cancelScheduledRedeliveries(states.keys)
    }

    override fun onPostCommit(updatedStates: Map<String, VerifyContractsRequestRedelivery?>) {
        val updates = updatedStates.filter { it.value != null }.map { it.value!! }
        val deletes = updatedStates.filter { it.value == null }

        scheduleRedeliveries(updates)
        cancelScheduledRedeliveries(deletes.keys)
    }

    private fun scheduleRedeliveries(redeliveries: Collection<VerifyContractsRequestRedelivery>) {
        redeliveries.forEach {
            val duration = Duration.between(Instant.now(), it.scheduledDelivery).toMillis()
            val scheduledRedelivery = scheduledExecutorService.schedule(
                { redeliverRequest(it.request) },
                duration,
                TimeUnit.MILLISECONDS
            )

            val requestId = it.request.flowExternalEventContext.requestId
            val existingRedelivery = this.redeliveries.put(requestId, scheduledRedelivery)
            existingRedelivery?.cancel(false)
        }
    }

    private fun cancelScheduledRedeliveries(requestIds: Collection<String>){
        requestIds.forEach {
            redeliveries.remove(it)?.cancel(false)
        }
    }

    private fun redeliverRequest(request: VerifyContractsRequest) {
        publisher?.publish(listOf(
            Record(
                topic = Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC,
                key = request.flowExternalEventContext.requestId,
                value = request
            )
        ))
    }
}