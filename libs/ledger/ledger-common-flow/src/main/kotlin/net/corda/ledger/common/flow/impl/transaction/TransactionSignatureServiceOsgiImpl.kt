package net.corda.ledger.common.flow.impl.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.internal.serialization.amqp.api.SerializationServiceInternal
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.common.flow.transaction.TransactionSignatureVerificationServiceInternal
import net.corda.ledger.libs.common.flow.impl.transaction.TransactionSignatureServiceImpl
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Suppress("Unused", "LongParameterList")
@Component(
    service = [TransactionSignatureService::class, TransactionSignatureServiceInternal::class, UsedByFlow::class],
    scope = ServiceScope.PROTOTYPE
)
class TransactionSignatureServiceOsgiImpl private constructor(
    delegate: TransactionSignatureServiceImpl
) : TransactionSignatureService,
    TransactionSignatureServiceInternal by delegate,
    SingletonSerializeAsToken,
    UsedByFlow {
    @Activate
    constructor(
        @Reference(service = SerializationServiceInternal::class)
        serializationService: SerializationServiceInternal,
        @Reference(service = SigningService::class)
        signingService: SigningService,
        @Reference(service = SignatureSpecService::class)
        signatureSpecService: SignatureSpecService,
        @Reference(service = MerkleTreeProvider::class)
        merkleTreeProvider: MerkleTreeProvider,
        @Reference(service = PlatformInfoProvider::class)
        platformInfoProvider: PlatformInfoProvider,
        @Reference(service = FlowEngine::class)
        flowEngine: FlowEngine,
        @Reference(service = TransactionSignatureVerificationServiceInternal::class)
        transactionSignatureVerificationServiceInternal: TransactionSignatureVerificationServiceInternal
    ) : this(
        TransactionSignatureServiceImpl(
            serializationService,
            signingService,
            signatureSpecService,
            merkleTreeProvider,
            platformInfoProvider,
            { flowEngine.getCpiSummary() },
            transactionSignatureVerificationServiceInternal
        )
    )
}
