package net.corda.flow.application.services.impl.interop.facade

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.common.json.serializers.standardTypesModule
import net.corda.flow.application.services.impl.interop.ProofOfActionSerialisationModule
import net.corda.flow.application.services.impl.interop.parameters.TypeParameters
import net.corda.flow.application.services.impl.interop.parameters.TypedParameterImpl
import net.corda.flow.application.services.impl.interop.parameters.TypedParameterValueImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.application.interop.parameters.TypedParameterValue
import java.io.StringWriter
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.Base64
import java.util.TimeZone

class FacadeRequestDeserializer : JsonDeserializer<FacadeRequest>() {

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): FacadeRequest =
        deserialize(parser, ::FacadeRequestImpl)

}

class FacadeResponseDeserializer : JsonDeserializer<FacadeResponseImpl>() {

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): FacadeResponseImpl =
        deserialize(parser, ::FacadeResponseImpl)

}

private val jsonMapper =
    JsonMapper.builder().enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES).build().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
        setTimeZone(TimeZone.getTimeZone("UTC"))
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        registerModule(ProofOfActionSerialisationModule.module)
        registerModule(standardTypesModule())
    }
@Suppress("ThrowsCount")
private fun <T> deserialize(
    parser: JsonParser,
    ctor: (FacadeId, String, List<TypedParameterValue<*>>) -> T
): T {
    val node = parser.codec.readTree<JsonNode>(parser)
    val method = node["method"]?.asText() ?: throw IllegalArgumentException(
        "No 'method' field in request/response ${node.toPrettyString()}"
    )

    val facadeId = try {
        FacadeId.of(method.substringBeforeLast("/"))
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException(
            "Invalid method id '$method' in ${node.toPrettyString()}"
        )
    }
    val methodName = method.substringAfterLast("/")

    val parameters = node["parameters"]?.fields()?.asSequence() ?: emptySequence()
    val parameterValues = parameters.map { (name, parameterValue) ->
        val typeName = parameterValue["type"]?.asText() ?: throw IllegalArgumentException(
            "No parameter type given for parameter $name in ${node.toPrettyString()}"
        )
        val type = TypeParameters.of<Any>(typeName)
        val value = parameterValue["value"] ?: throw IllegalArgumentException(
            "No parameter value given for parameter $name in ${node.toPrettyString()}"
        )
        val paramValue = type.readValue(name, value, parser)
        TypedParameterValueImpl(TypedParameterImpl(name, type), paramValue)
    }.toList()

    return ctor(facadeId, methodName, parameterValues)
}

fun ParameterType<*>.readValue(name: String, node: JsonNode, parser: JsonParser): Any =
    if (this.isQualified) {
        this.rawParameterType.readValue(name, node, parser)
    } else {
        when (this.typeLabel) {
            ParameterTypeLabel.BOOLEAN -> if (node.isBoolean) node.asBoolean() else throw IllegalArgumentException(
                "Parameter $name expected to have a boolean value, but was ${node.toPrettyString()}"
            )
            ParameterTypeLabel.STRING -> node.asText()
            ParameterTypeLabel.DECIMAL -> try {
                BigDecimal(node.asText())
            } catch (_: NumberFormatException) {
                throw IllegalArgumentException(
                    "Parameter $name expected to have a decimal value, but was ${node.toPrettyString()}"
                )
            }
            ParameterTypeLabel.UUID -> try {
                UUID.fromString(node.asText())
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Parameter $name expected to have a UUID value, but was ${node.toPrettyString()}")
            }
            ParameterTypeLabel.TIMESTAMP -> try {
                ZonedDateTime.parse(node.asText(), DateTimeFormatter.ISO_DATE_TIME)
            } catch (_: DateTimeParseException) {
                throw IllegalArgumentException(
                    "Parameter $name expected to have a timestamp value in ISO_DATE_TIME format " +
                            "but was ${node.toPrettyString()}"
                )
            }
            ParameterTypeLabel.BYTES -> ByteBuffer.wrap(
                try {
                    Base64.getDecoder().decode(node.asText())
                } catch (_: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "Parameter $name expected to have a Base64-encoded byte blob value, but was " +
                                node.toPrettyString()
                    )
                }
            )

            ParameterTypeLabel.JSON -> {
                val writer = StringWriter()
                parser.codec.factory.createGenerator(writer).writeTree(node)
                writer.flush()
                writer.toString()
            }

            //TODO THIS MUST BE WRONG
            ParameterTypeLabel.SIGNED_TX -> {
                jsonMapper.readValues(parser, DigitalSignatureAndMetadata::class.java)

            }
        }
    }