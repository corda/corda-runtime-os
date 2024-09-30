package net.corda.ledger.utxo.flow.impl.transaction.factory.impl

import net.corda.flow.application.GroupParametersLookupInternal
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.flow.transaction.PrivacySaltProviderService
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.ledger.lib.utxo.flow.impl.groupparameters.SignedGroupParametersVerifier
import net.corda.ledger.lib.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.lib.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.lib.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.lib.utxo.flow.impl.transaction.factory.impl.UtxoSignedTransactionFactoryImpl
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.NotarySignatureVerificationServiceInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.libs.json.validator.JsonValidator
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import org.osgi.service.component.annotations.ServiceScope

@Suppress("LongParameterList")
@Component(service = [UtxoSignedTransactionFactory::class, UsedByFlow::class], scope = ServiceScope.PROTOTYPE)
class UtxoSignedTransactionFactoryOsgiImpl(
    delegate: UtxoSignedTransactionFactory
) :
    UtxoSignedTransactionFactory by delegate, UsedByFlow, SingletonSerializeAsToken {

    @Suppress("Unused")
    @Activate
    constructor(
        @Reference(service = CurrentSandboxGroupContext::class)
        currentSandboxGroupContext: CurrentSandboxGroupContext,
        @Reference(service = JsonMarshallingService::class, scope = PROTOTYPE_REQUIRED)
        jsonMarshallingService: JsonMarshallingService,
        @Reference(service = JsonValidator::class, scope = PROTOTYPE_REQUIRED)
        jsonValidator: JsonValidator,
        @Reference(service = SerializationService::class)
        serializationService: SerializationService,
        @Reference(service = TransactionSignatureServiceInternal::class)
        transactionSignatureService: TransactionSignatureServiceInternal,
        @Reference(service = TransactionMetadataFactory::class)
        transactionMetadataFactory: TransactionMetadataFactory,
        @Reference(service = WireTransactionFactory::class)
        wireTransactionFactory: WireTransactionFactory,
        @Reference(service = UtxoLedgerTransactionFactory::class)
        utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory,
        @Reference(service = UtxoLedgerTransactionVerificationService::class)
        utxoLedgerTransactionVerificationService: UtxoLedgerTransactionVerificationService,
        @Reference(service = UtxoLedgerGroupParametersPersistenceService::class)
        utxoLedgerGroupParametersPersistenceService: UtxoLedgerGroupParametersPersistenceService,
        @Reference(service = GroupParametersLookupInternal::class)
        groupParametersLookup: GroupParametersLookupInternal,
        @Reference(service = SignedGroupParametersVerifier::class)
        signedGroupParametersVerifier: SignedGroupParametersVerifier,
        @Reference(service = NotarySignatureVerificationServiceInternal::class)
        notarySignatureVerificationService: NotarySignatureVerificationServiceInternal,
        @Reference(service = PrivacySaltProviderService::class)
        privacySaltProviderService: PrivacySaltProviderService
    ) : this(
        UtxoSignedTransactionFactoryImpl(
            currentSandboxGroupContext,
            jsonMarshallingService,
            jsonValidator,
            serializationService,
            transactionSignatureService,
            transactionMetadataFactory,
            wireTransactionFactory,
            utxoLedgerTransactionFactory,
            utxoLedgerTransactionVerificationService,
            utxoLedgerGroupParametersPersistenceService,
            groupParametersLookup,
            signedGroupParametersVerifier,
            notarySignatureVerificationService,
            privacySaltProviderService
        )
    )
}
