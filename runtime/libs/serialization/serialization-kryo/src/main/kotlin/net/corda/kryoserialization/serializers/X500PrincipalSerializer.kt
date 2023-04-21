package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import javax.security.auth.x500.X500Principal

/**
 * For general use X500Principal class manages serialization/deserialization internally by way of being a
 * java.io.Serializable and using transient fields. Using Kryo's stock JavaSerializer is not recommended because it's
 * inefficient, but regardless causes an exception to be thrown on deserialization when used with Corda.
 * It is actually trivial to serialize/deserialize objects of this type as the whole content is encapsulated in the
 * name.
 */
class X500PrincipalSerializer: Serializer<X500Principal>() {
    override fun write(kryo: Kryo, output: Output, obj: X500Principal) {
        output.writeString(obj.name)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out X500Principal>): X500Principal {
        return X500Principal(input.readString())
    }
}
