package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.apache.avro.specific.SpecificRecordBase

internal object AvroRecordSerializer : Serializer<SpecificRecordBase>() {
    override fun write(kryo: Kryo, output: Output, avroSerializable: SpecificRecordBase) {
        val message =
            "${avroSerializable.javaClass.name} is an avro generated class and should never be serialised into a checkpoint."
        throw UnsupportedOperationException(message)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<SpecificRecordBase>) =
        throw IllegalStateException("Should not reach here!")
}
