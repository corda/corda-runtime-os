package net.corda.ledger.common.impl.transaction.serializer

import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.common.transaction.PrivacySalt

class WireTransactionKryoSerializer(
    private val merkleTreeFactory: MerkleTreeFactory,
    private val digestService: DigestService
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
            privacySalt,
            componentGroupLists,
        )
    }
}

