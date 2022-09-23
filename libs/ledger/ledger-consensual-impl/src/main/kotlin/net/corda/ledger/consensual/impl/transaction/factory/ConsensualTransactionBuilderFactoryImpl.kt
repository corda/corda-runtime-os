package net.corda.ledger.consensual.impl.transaction.factory

import net.corda.ledger.consensual.impl.transaction.ConsensualTransactionBuilderImpl
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Suppress("LongParameterList")
@Component(service = [ConsensualTransactionBuilderFactory::class], scope = ServiceScope.PROTOTYPE)
class ConsensualTransactionBuilderFactoryImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class) private val cipherSchemeMetadata: CipherSchemeMetadata,
    @Reference(service = DigestService::class) private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class) private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = MerkleTreeProvider::class) private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = SerializationService::class) private val serializationService: SerializationService,
    @Reference(service = SigningService::class) private val signingService: SigningService
) : ConsensualTransactionBuilderFactory {

    override fun create(): ConsensualTransactionBuilder {
        return ConsensualTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeProvider,
            serializationService,
            signingService
        )
    }
}