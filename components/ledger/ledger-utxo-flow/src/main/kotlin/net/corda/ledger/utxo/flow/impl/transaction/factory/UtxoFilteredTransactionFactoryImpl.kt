package net.corda.ledger.utxo.flow.impl.transaction.factory

import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.flow.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup.METADATA
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.flow.impl.transaction.UtxoFilteredTransactionBuilderInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey

@Component(
    service = [UtxoFilteredTransactionFactory::class, UsedByFlow::class],
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
        return UtxoFilteredTransactionImpl(
            // the infos filter needs to be that it matches the indexes kept from the filtering of the data
            serializationService,
            filteredTransactionFactory.create(
                signedTransaction.wireTransaction,
                listOfNotNull(
                    ComponentGroupFilterParameters.AuditProof(METADATA.ordinal, TransactionMetadata::class.java) { true },
                    filteredTransactionBuilder.notaryPredicate?.let { predicate ->
                        ComponentGroupFilterParameters.AuditProof(
                            UtxoComponentGroup.NOTARY.ordinal,
                            Any::class.java,
                        ) { deserialized -> (deserialized as? Party)?.let { predicate.test(it) } ?: true }
                    },
                    filteredTransactionBuilder.signatoriesPredicate?.let { predicate ->
                        ComponentGroupFilterParameters.AuditProof(
                            UtxoComponentGroup.SIGNATORIES.ordinal,
                            PublicKey::class.java,
                            predicate
                        )
                    },
                    filteredTransactionBuilder.inputStatesPredicate?.let { predicate ->
                        ComponentGroupFilterParameters.AuditProof(
                            UtxoComponentGroup.INPUTS.ordinal,
                            StateRef::class.java,
                            predicate
                        )
                    },
                    filteredTransactionBuilder.referenceInputStatesPredicate?.let { predicate ->
                        ComponentGroupFilterParameters.AuditProof(
                            UtxoComponentGroup.REFERENCES.ordinal,
                            StateRef::class.java,
                            predicate
                        )
                    },
                    filteredTransactionBuilder.outputStatesPredicate?.let { _ ->
                        ComponentGroupFilterParameters.AuditProof(
                            UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                            UtxoOutputInfoComponent::class.java
                        ) { true } // TODO Do not include all infos?
                    },
//                    filteredTransactionBuilder.outputStatesPredicate?.let { predicate ->
//                        ComponentGroupFilterParameters.AuditProof(
//                            UtxoComponentGroup.OUTPUTS.ordinal,
//                            StateAndRef::class.java,
//                            predicate
//                        )
//                    },
                    filteredTransactionBuilder.outputStatesPredicate?.let { _ ->
                        ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.OUTPUTS.ordinal)
                    },
                    filteredTransactionBuilder.commandsPredicate?.let { _ ->
                        ComponentGroupFilterParameters.AuditProof(
                            UtxoComponentGroup.COMMANDS_INFO.ordinal,
                            List::class.java
                        ) { true } // TODO Do not include all infos?
                    },
                    filteredTransactionBuilder.commandsPredicate?.let { predicate ->
                        ComponentGroupFilterParameters.AuditProof(
                            UtxoComponentGroup.COMMANDS.ordinal,
                            Command::class.java,
                            predicate
                        )
                    }
                )
            )
        )
    }
}