package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.kryoserialization.readBytesWithLength
import net.corda.kryoserialization.writeBytesWithLength
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl

class PrivacySaltImplSerializer : Serializer<PrivacySaltImpl>() {
    override fun write(kryo: Kryo, output: Output, obj: PrivacySaltImpl) {
        output.writeBytesWithLength(obj.bytes)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<PrivacySaltImpl>): PrivacySaltImpl {
        return PrivacySaltImpl(input.readBytesWithLength())
    }
}

