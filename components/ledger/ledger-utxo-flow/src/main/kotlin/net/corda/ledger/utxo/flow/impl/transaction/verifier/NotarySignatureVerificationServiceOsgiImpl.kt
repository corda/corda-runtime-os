package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.ledger.common.flow.transaction.TransactionSignatureVerificationServiceInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.NotarySignatureVerificationServiceImpl
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.NotarySignatureVerificationServiceInternal
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [
        NotarySignatureVerificationService::class,
        NotarySignatureVerificationServiceInternal::class,
        UsedByFlow::class,
        UsedByVerification::class
    ],
    scope = PROTOTYPE
)
class NotarySignatureVerificationServiceOsgiImpl(
    delegate: NotarySignatureVerificationServiceImpl,
) : NotarySignatureVerificationService,
    NotarySignatureVerificationServiceInternal by delegate,
    UsedByFlow,
    UsedByVerification,
    SingletonSerializeAsToken {

    @Suppress("Unused")
    @Activate
    constructor(
        @Reference(service = TransactionSignatureVerificationServiceInternal::class)
        transactionSignatureService: TransactionSignatureVerificationServiceInternal
    ) : this(NotarySignatureVerificationServiceImpl(transactionSignatureService))
}
