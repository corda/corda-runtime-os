package net.corda.ledger.common.impl.transaction.serializer

import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.common.transaction.PrivacySalt
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CheckpointInternalCustomSerializer::class])
class WireTransactionKryoSerializer @Activate constructor(
    @Reference(service = MerkleTreeFactory::class) private val merkleTreeFactory: MerkleTreeFactory,
    @Reference(service = DigestService::class) private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class) private val jsonMarshallingService: JsonMarshallingService
) : CheckpointInternalCustomSerializer<WireTransaction> {
    override val type: Class<WireTransaction> get() = WireTransaction::class.java

    override fun write(output: CheckpointOutput, obj: WireTransaction) {
        output.writeClassAndObject(obj.privacySalt)
        output.writeClassAndObject(obj.componentGroupLists)
    }

    override fun read(input: CheckpointInput, type: Class<WireTransaction>): WireTransaction {
        val privacySalt = input.readClassAndObject() as PrivacySalt
        val componentGroupLists : List<List<ByteArray>> = uncheckedCast(input.readClassAndObject())
        return WireTransaction(
            merkleTreeFactory,
            digestService,
            jsonMarshallingService,
            privacySalt,
            componentGroupLists,
        )
    }
}

