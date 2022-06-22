package net.corda.ledger.consensual

import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE


@Component(service = [ConsensualLedgerService::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class ConsensualLedgerServiceImpl @Activate constructor() : ConsensualLedgerService, SingletonSerializeAsToken {
    private companion object {
        val logger = contextLogger()
        init {
            println("ConsensualLedgerServiceImpl init static")
        }
    }

    override fun double(n: Int): Int {
        return n*2
    }
}
