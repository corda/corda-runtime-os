package net.corda.ledger.utxo.impl.token.selection.impl

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.utxo.impl.token.selection.factories.TokenClaimQueryExternalEventFactory
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.token.selection.TokenClaim
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [TokenSelection::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class TokenSelectionImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor
) : TokenSelection, SingletonSerializeAsToken {

    @Suspendable
    override fun tryClaim(criteria: TokenClaimCriteria): TokenClaim? {
        return externalEventExecutor.execute(
            TokenClaimQueryExternalEventFactory::class.java,
            criteria
        )
    }
}

