package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.ClosureSerializer

internal object CordaClosureBlacklistSerializer : ClosureSerializer() {
    const val ERROR_MESSAGE = "Java 8 Lambda expressions are not supported for serialization."

    override fun write(kryo: Kryo, output: Output, target: Any) {
        throw IllegalArgumentException(ERROR_MESSAGE)
    }
}
