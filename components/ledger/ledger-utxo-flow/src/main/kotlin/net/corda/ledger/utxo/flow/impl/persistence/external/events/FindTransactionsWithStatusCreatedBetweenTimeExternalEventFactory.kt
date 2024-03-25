package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindTransactionsWithStatusCreatedBetweenTime
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock
import java.time.Instant

@Component(service = [ExternalEventFactory::class])
class FindTransactionsWithStatusCreatedBetweenTimeExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<FindTransactionsWithStatusCreatedBetweenTimeParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindTransactionsWithStatusCreatedBetweenTimeParameters): Any {
        return FindTransactionsWithStatusCreatedBetweenTime.newBuilder()
            .setTransactionStatus(parameters.status.value)
            .setFrom(parameters.from)
            .setUntil(parameters.until)
            .setLimit(parameters.limit)
            .build()
    }
}

@CordaSerializable
data class FindTransactionsWithStatusCreatedBetweenTimeParameters(
    val status: TransactionStatus,
    val from: Instant,
    val until: Instant,
    val limit: Int,
)
