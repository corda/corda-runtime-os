package net.corda.simulator.runtime.utils

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.simulator.runtime.signing.pemDecode
import net.corda.simulator.runtime.signing.pemEncode
import java.security.PublicKey

@Suppress("ForbiddenComment")
/*
 * TODO: Remove this whole module once SerializationService replaces Json service
 *  in ConsensualStateLedgerService and friends.
 */
internal object PublicKeyDeserializer : JsonDeserializer<PublicKey>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext?): PublicKey {
        return try {
            pemDecode(parser.text)
        } catch (e: Exception) {
            throw JsonParseException(parser, e.message, e)
        }
    }
}
internal object PublicKeySerializer : JsonSerializer<PublicKey>() {
    override fun serialize(
        value: PublicKey,
        gen: JsonGenerator,
        serializers: SerializerProvider?
    ) {
        gen.writeString(pemEncode(value))
    }
}

fun publicKeyModule(): Module {
    return SimpleModule("Simulator public keys")
        .addDeserializer(PublicKey::class.java, PublicKeyDeserializer)
        .addSerializer(PublicKey::class.java, PublicKeySerializer)
}
