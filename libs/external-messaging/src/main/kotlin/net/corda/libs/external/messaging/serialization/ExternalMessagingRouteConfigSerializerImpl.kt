package net.corda.libs.external.messaging.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.corda.crypto.core.parseSecureHash
import net.corda.libs.external.messaging.entities.RouteConfiguration
import net.corda.libs.packaging.core.CpiIdentifier
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalMessagingRouteConfigSerializer::class])
class ExternalMessagingRouteConfigSerializerImpl : ExternalMessagingRouteConfigSerializer {
    private val objectMapper = ObjectMapper().apply {
        val module = SimpleModule()
        module.addSerializer(CpiIdentifier::class.java, CpiIdentifierSerializer())
        module.addDeserializer(CpiIdentifier::class.java, CpiIdentifierDeserializer())

        this.registerModule(module)
    }

    override fun serialize(routeConfiguration: RouteConfiguration): String {
        return objectMapper.writeValueAsString(routeConfiguration)
    }

    override fun deserialize(routeConfiguration: String): RouteConfiguration {
        return objectMapper.readValue(routeConfiguration, RouteConfiguration::class.java)
    }

    class CpiIdentifierDeserializer(vc: Class<*>? = null) : StdDeserializer<CpiIdentifier>(vc) {

        override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): CpiIdentifier {
            checkNotNull(p) {
                "null JsonParser while trying to Deserialize CpiIdentifier"
            }
            val node: JsonNode = p.codec.readTree(p)
            val name = node.get("name").textValue()
            val version = node.get("version").textValue()
            val signerSummaryHash = node.get("signerSummaryHash").textValue()
            return CpiIdentifier(name, version, parseSecureHash(signerSummaryHash))
        }
    }

    class CpiIdentifierSerializer(t: Class<*>? = null, dummy: Boolean = false) : StdSerializer<CpiIdentifier>(t, dummy) {
        override fun serialize(value: CpiIdentifier?, gen: JsonGenerator?, provider: SerializerProvider?) {
            checkNotNull(gen) {
                "null JsonGenerator while trying to Serialize CpiIdentifier"
            }
            checkNotNull(value) {
                "null CpiIdentifier while trying to Serialize CpiIdentifier"
            }

            gen.writeStartObject()
            gen.writeStringField("name", value.name)
            gen.writeStringField("version", value.version)
            gen.writeStringField("signerSummaryHash", value.signerSummaryHash.toString())
            gen.writeEndObject()
        }
    }
}
