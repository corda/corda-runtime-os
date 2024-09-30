package net.corda.ledger.utxo.flow.impl.transaction.factory.impl

import net.corda.flow.application.GroupParametersLookupInternal
import net.corda.ledger.lib.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.lib.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
import net.corda.ledger.lib.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.lib.utxo.flow.impl.transaction.factory.impl.UtxoLedgerTransactionFactoryImpl
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [UtxoLedgerTransactionFactory::class, UsedByFlow::class],
    scope = PROTOTYPE,
    property = [CORDA_SYSTEM_SERVICE],
)
class UtxoLedgerTransactionFactoryOsgiImpl(
    delegate: UtxoLedgerTransactionFactory
) : UtxoLedgerTransactionFactory by delegate, UsedByFlow, SingletonSerializeAsToken {

    @Suppress("Unused")
    @Activate
    constructor(
        @Reference(service = SerializationService::class)
        serializationService: SerializationService,
        @Reference(service = UtxoLedgerStateQueryService::class)
        utxoLedgerStateQueryService: UtxoLedgerStateQueryService,
        @Reference(service = UtxoLedgerGroupParametersPersistenceService::class)
        utxoLedgerGroupParametersPersistenceService: UtxoLedgerGroupParametersPersistenceService,
        @Reference(service = GroupParametersLookupInternal::class)
        groupParametersLookup: GroupParametersLookupInternal
    ) : this(
        UtxoLedgerTransactionFactoryImpl(
            serializationService,
            utxoLedgerStateQueryService,
            utxoLedgerGroupParametersPersistenceService,
            groupParametersLookup
        )
    )
}
