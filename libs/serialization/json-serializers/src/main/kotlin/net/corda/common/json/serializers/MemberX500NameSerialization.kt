package net.corda.common.json.serializers

import java.io.IOException
import java.io.UncheckedIOException
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonNodeReader
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.application.marshalling.json.JsonWriter
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Component

/**
 * This serializer is global scope, the same instance will be provided to all sandboxes.
 */
@Component
class MemberX500NameDeserializer : JsonDeserializer<MemberX500Name> {
    override fun deserialize(jsonRoot: JsonNodeReader): MemberX500Name {
        val text = jsonRoot.asText()
        try {
            return MemberX500Name.parse(text)
        } catch (e: Exception) {
            throw CordaRuntimeException("Failed to turn text from Json: '$text' into a MemberX500Name")
        }
    }
}

/**
 * This serializer is global scope, the same instance will be provided to all sandboxes.
 */
@Component
class MemberX500NameSerializer : JsonSerializer<MemberX500Name> {
    override fun serialize(item: MemberX500Name, jsonWriter: JsonWriter) {
        return try {
            jsonWriter.writeString(item.toString())
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }
}
