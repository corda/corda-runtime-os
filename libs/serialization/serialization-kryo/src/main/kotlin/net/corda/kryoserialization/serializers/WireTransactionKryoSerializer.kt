package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.common.transaction.PrivacySalt

class WireTransactionKryoSerializer(
    private val merkleTreeFactory: MerkleTreeFactory,
    private val digestService: DigestService
) : Serializer<WireTransaction>() {
    override fun write(kryo: Kryo, output: Output, obj: WireTransaction) {
        kryo.writeClassAndObject(output, obj.privacySalt)
        kryo.writeClassAndObject(output, obj.componentGroupLists)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<WireTransaction>): WireTransaction {
        val privacySalt = kryo.readClassAndObject(input) as PrivacySalt
        val componentGroupLists : List<List<ByteArray>> = uncheckedCast(kryo.readClassAndObject(input))
        return WireTransaction(
            merkleTreeFactory,
            digestService,
            privacySalt,
            componentGroupLists,
        )
    }
}

