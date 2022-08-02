package net.corda.ledger.consensual

import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE


@Component(service = [ConsensualLedgerService::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class ConsensualLedgerServiceImpl @Activate constructor() : ConsensualLedgerService, SingletonSerializeAsToken {
    override fun double(n: Int): Int {
        return n*2
    }
}
