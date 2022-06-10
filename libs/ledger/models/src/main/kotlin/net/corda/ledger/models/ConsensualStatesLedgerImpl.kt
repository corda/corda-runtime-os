package net.corda.ledger.models

import net.corda.v5.ledger.models.ConsensualStatesLedger
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE


@Component(service = [ConsensualStatesLedger::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class ConsensualStatesLedgerImpl @Activate constructor() : ConsensualStatesLedger, SingletonSerializeAsToken {
    override fun double(n: Int): Int {
        return n*2
    }
}