package net.corda.ledger.utxo.impl.token.selection.impl

import java.math.BigDecimal
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.utxo.impl.token.selection.factories.TokenBalanceQueryExternalEventFactory
import net.corda.ledger.utxo.impl.token.selection.factories.TokenClaimQueryExternalEventFactory
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import net.corda.v5.ledger.utxo.token.selection.TokenClaim
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [TokenSelection::class, UsedByFlow::class], scope = ServiceScope.PROTOTYPE)
class TokenSelectionImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor
) : TokenSelection, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun tryClaim(criteria: TokenClaimCriteria): TokenClaim? {
        // This generates a message to try to claim the tokens based on the filter
        // The message is sent by the external event executor and the response is also received by the executer
        // The `execute` is a blocking call and will return when the message is received or an exception is raised
        // after a timeout
        // The flow goes to sleep until the response arrives
        return externalEventExecutor.execute(
            TokenClaimQueryExternalEventFactory::class.java,
            criteria
        )
    }

    @Suspendable
    override fun queryBalance(utxoTokenPoolKey: UtxoTokenPoolKey): BigDecimal {
        return externalEventExecutor.execute(
            TokenBalanceQueryExternalEventFactory::class.java,
            utxoTokenPoolKey
        )
    }
}

