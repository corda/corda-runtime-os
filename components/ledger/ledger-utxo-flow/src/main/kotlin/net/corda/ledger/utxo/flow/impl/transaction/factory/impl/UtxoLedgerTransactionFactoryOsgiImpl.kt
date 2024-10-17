package net.corda.ledger.utxo.flow.impl.transaction.factory.impl

import net.corda.crypto.core.parseSecureHash
import net.corda.flow.application.GroupParametersLookupInternal
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.lib.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
import net.corda.ledger.lib.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.lib.utxo.flow.impl.transaction.factory.impl.UtxoLedgerTransactionFactoryImpl
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
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
        ) { wireTransaction ->
            val membershipGroupParametersHashString =
                requireNotNull((wireTransaction.metadata as TransactionMetadataInternal).getMembershipGroupParametersHash()) {
                    "Membership group parameters hash cannot be found in the transaction metadata."
                }
            val currentGroupParameters = groupParametersLookup.currentGroupParameters
            val groupParameters =
                if (currentGroupParameters.hash.toString() == membershipGroupParametersHashString) {
                    currentGroupParameters
                } else {
                    val membershipGroupParametersHash = parseSecureHash(membershipGroupParametersHashString)
                    utxoLedgerGroupParametersPersistenceService.find(membershipGroupParametersHash)
                }
            requireNotNull(groupParameters) {
                "Signed group parameters $membershipGroupParametersHashString related to the transaction " +
                    "${wireTransaction.id} cannot be accessed."
            }
            groupParameters
        }
    )
}
