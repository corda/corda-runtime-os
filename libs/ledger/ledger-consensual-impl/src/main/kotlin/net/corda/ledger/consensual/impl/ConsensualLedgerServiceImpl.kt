package net.corda.ledger.consensual.impl

import net.corda.flow.fiber.FlowFiberService
import net.corda.ledger.consensual.impl.transaction.ConsensualTransactionBuilderImpl
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [ConsensualLedgerService::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class ConsensualLedgerServiceImpl @Activate constructor(
    @Reference(service = MerkleTreeFactory::class) private val merkleTreeFactory: MerkleTreeFactory,
    @Reference(service = DigestService::class) private val digestService: DigestService,
    @Reference(service = SigningService::class) private val signingService: SigningService,
    @Reference(service = FlowFiberService::class) private val flowFiberService: FlowFiberService,
    @Reference(service = CipherSchemeMetadata::class) private val schemeMetadata: CipherSchemeMetadata
    ): ConsensualLedgerService, SingletonSerializeAsToken {

    override fun getTransactionBuilder(): ConsensualTransactionBuilder {
        val secureRandom = schemeMetadata.secureRandom
        val serializer = flowFiberService.getExecutingFiber().getExecutionContext().sandboxGroupContext.amqpSerializer
        return ConsensualTransactionBuilderImpl(merkleTreeFactory, digestService, secureRandom, serializer, signingService)
    }
}
