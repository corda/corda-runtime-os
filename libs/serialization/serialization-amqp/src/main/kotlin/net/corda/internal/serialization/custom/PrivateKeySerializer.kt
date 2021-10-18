package net.corda.internal.serialization.custom

import net.corda.v5.serialization.SerializationCustomSerializer
import java.security.PrivateKey

object PrivateKeySerializer : SerializationCustomSerializer<PrivateKey, String> {
    override fun toProxy(obj: PrivateKey): String = throw IllegalStateException("Attempt to serialise private key")
    override fun fromProxy(proxy: String): PrivateKey = throw IllegalStateException("Attempt to deserialise private key")
}