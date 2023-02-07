package net.corda.interop.data

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import java.io.StringWriter
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

//TODO : All facade classed are copied from WEFT project, and in future it can be replaced by facade component

class FacadeRequestDeserializer : JsonDeserializer<FacadeRequest>() {

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): FacadeRequest =
        deserialize(parser,
            ::FacadeRequest)

}

class FacadeResponseDeserializer : JsonDeserializer<FacadeResponse>() {

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): FacadeResponse =
        deserialize(parser,
            ::FacadeResponse)

}

private fun <T> deserialize(parser: JsonParser,
                            ctor: (FacadeId, String, List<FacadeParameterValue<*>>) -> T): T {
    val node = parser.codec.readTree<JsonNode>(parser)
    val method = node["method"].asText()

    val facadeId = FacadeId.of(method.substringBeforeLast("/"))
    val methodName = method.substringAfterLast("/")

    val parameters = node["parameters"]
    val parameterValues = parameters.fields().asSequence().map { (name, value) ->
        val type = FacadeParameterType.of<Any>(value["type"].asText())
        val paramValue = type.readValue(value["value"], parser)
        FacadeParameterValue(FacadeParameter(name, type), paramValue)
    }.toList()

    return ctor(facadeId, methodName, parameterValues)
}

fun FacadeParameterType<*>.readValue(node: JsonNode, parser: JsonParser): Any = when(this) {
    is FacadeParameterType.BooleanType -> node.asBoolean()
    is FacadeParameterType.StringType -> node.asText()
    is FacadeParameterType.DecimalType -> node.asBigDecimal()
    is FacadeParameterType.UUIDType -> UUID.fromString(node.asText())
    is FacadeParameterType.TimestampType -> ZonedDateTime.parse(node.asText(), DateTimeFormatter.ISO_DATE_TIME)
    is FacadeParameterType.ByteBufferType -> ByteBuffer.wrap(
        Base64.getDecoder().decode(node.asText()))
    is FacadeParameterType.JsonType -> {
        val writer = StringWriter()
        parser.codec.factory.createGenerator(writer).writeTree(node)
        writer.flush()
        writer.toString()
    }
    is FacadeParameterType.QualifiedType -> type.readValue(node, parser)
}

fun JsonNode.asBigDecimal(): BigDecimal = BigDecimal(asText())