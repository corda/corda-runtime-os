package net.corda.ledger.libs.common.flow.impl.transaction.kryo

import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput

class WireTransactionKryoSerializer(
    private val wireTransactionFactory: WireTransactionFactory
) : CheckpointInternalCustomSerializer<WireTransaction>, UsedByFlow {
    override val type = WireTransaction::class.java

    override fun write(output: CheckpointOutput, obj: WireTransaction) {
        output.writeClassAndObject(obj.privacySalt)
        output.writeClassAndObject(obj.componentGroupLists)
    }

    override fun read(input: CheckpointInput, type: Class<out WireTransaction>): WireTransaction {
        val privacySalt = input.readClassAndObject() as PrivacySalt

        @Suppress("unchecked_cast")
        val componentGroupLists = input.readClassAndObject() as List<List<ByteArray>>
        return wireTransactionFactory.create(
            componentGroupLists,
            privacySalt,
        )
    }
}
