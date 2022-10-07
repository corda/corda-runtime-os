package net.corda.ledger.consensual.impl

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.fiber.FlowFiberService
import net.corda.ledger.consensual.impl.transaction.ConsensualTransactionBuilderImpl
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Suppress("LongParameterList")
@Component(service = [ConsensualLedgerService::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class ConsensualLedgerServiceImpl @Activate constructor(
    @Reference(service = MerkleTreeProvider::class) private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = DigestService::class) private val digestService: DigestService,
    @Reference(service = SigningService::class) private val signingService: SigningService,
    @Reference(service = FlowFiberService::class) private val flowFiberService: FlowFiberService,
    @Reference(service = CipherSchemeMetadata::class) private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = JsonMarshallingService::class) private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = MemberLookup::class) private val memberLookup: MemberLookup,
    @Reference(service = CpiInfoReadService::class) private val cpiInfoService: CpiInfoReadService,
    @Reference(service = VirtualNodeInfoReadService::class) private val virtualNodeInfoService: VirtualNodeInfoReadService,
    ): ConsensualLedgerService, SingletonSerializeAsToken {

    override fun getTransactionBuilder(): ConsensualTransactionBuilder {
        val secureRandom = schemeMetadata.secureRandom
        val serializer = flowFiberService.getExecutingFiber().getExecutionContext().sandboxGroupContext.amqpSerializer
        return ConsensualTransactionBuilderImpl(
            merkleTreeProvider,
            digestService,
            secureRandom,
            serializer,
            signingService,
            jsonMarshallingService,
            memberLookup,
            cpiInfoService,
            virtualNodeInfoService,
        )
    }
}
