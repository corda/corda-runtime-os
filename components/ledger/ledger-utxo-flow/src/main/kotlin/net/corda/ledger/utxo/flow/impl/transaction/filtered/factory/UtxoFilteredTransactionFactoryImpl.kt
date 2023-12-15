package net.corda.ledger.utxo.flow.impl.transaction.filtered.factory

import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters.AuditProof.AuditProofPredicate
import net.corda.ledger.common.flow.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup.METADATA
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup.NOTARY
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionBuilderInternal
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionImpl
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey

@Component(
    service = [UtxoFilteredTransactionFactory::class, UsedByFlow::class],
    property = [SandboxConstants.CORDA_UNINJECTABLE_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class UtxoFilteredTransactionFactoryImpl @Activate constructor(
    @Reference(service = FilteredTransactionFactory::class)
    private val filteredTransactionFactory: FilteredTransactionFactory,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : UtxoFilteredTransactionFactory, UsedByFlow {

    @Suspendable
    override fun create(
        signedTransaction: UtxoSignedTransactionInternal,
        filteredTransactionBuilder: UtxoFilteredTransactionBuilderInternal
    ): UtxoFilteredTransaction {
        val notaryAndTimeWindow = if (filteredTransactionBuilder.notary || filteredTransactionBuilder.timeWindow) {
            ComponentGroupFilterParameters.AuditProof(
                NOTARY.ordinal,
                Any::class.java,
                AuditProofPredicate.Content {
                    filteredTransactionBuilder.notary && (it is MemberX500Name || it is PublicKey) || // notary components
                        filteredTransactionBuilder.timeWindow && it is TimeWindow // time window
                }
            )
        } else {
            null
        }
        return UtxoFilteredTransactionImpl(
            serializationService,
            filteredTransactionFactory.create(
                signedTransaction.wireTransaction,
                listOfNotNull(
                    ComponentGroupFilterParameters.AuditProof(
                        METADATA.ordinal,
                        TransactionMetadata::class.java,
                        AuditProofPredicate.Content { true }
                    ),
                    notaryAndTimeWindow,
                    filteredTransactionBuilder.signatories,
                    filteredTransactionBuilder.inputStates,
                    filteredTransactionBuilder.referenceStates,
                    (filteredTransactionBuilder.outputStates as? ComponentGroupFilterParameters.AuditProof<*>)?.let { _ ->
                        ComponentGroupFilterParameters.AuditProof(
                            UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                            UtxoOutputInfoComponent::class.java,
                            AuditProofPredicate.Content { true }
                        )
                    },
                    filteredTransactionBuilder.outputStates,
                    (filteredTransactionBuilder.commands as? ComponentGroupFilterParameters.AuditProof<*>)?.let { _ ->
                        ComponentGroupFilterParameters.AuditProof(
                            UtxoComponentGroup.COMMANDS_INFO.ordinal,
                            List::class.java,
                            AuditProofPredicate.Content { true }
                        )
                    },
                    filteredTransactionBuilder.commands
                )
            )
        )
    }
}
