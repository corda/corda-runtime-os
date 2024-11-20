package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.NotarySignatureVerificationServiceInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.UtxoSignedTransactionSignatureVerificationService
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.UtxoSignedTransactionSignatureVerificationServiceImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [UtxoSignedTransactionSignatureVerificationService::class, UsedByFlow::class], scope = PROTOTYPE)
@Suppress("unused")
class UtxoSignedTransactionSignatureVerificationServiceOsgiImpl private constructor(
    delegate: UtxoSignedTransactionSignatureVerificationService
) : UtxoSignedTransactionSignatureVerificationService by delegate, UsedByFlow, SingletonSerializeAsToken {

    @Activate
    @Suppress("unused")
    constructor(
        @Reference(service = NotarySignatureVerificationServiceInternal::class)
        notarySignatureVerificationService: NotarySignatureVerificationServiceInternal,
        @Reference(service = TransactionSignatureServiceInternal::class)
        transactionSignatureServiceInternal: TransactionSignatureServiceInternal
    ) : this(
        UtxoSignedTransactionSignatureVerificationServiceImpl(notarySignatureVerificationService, transactionSignatureServiceInternal)
    )
}