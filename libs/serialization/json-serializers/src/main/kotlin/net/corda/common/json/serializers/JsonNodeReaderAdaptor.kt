package net.corda.common.json.serializers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import net.corda.v5.application.marshalling.json.JsonNodeReader
import net.corda.v5.application.marshalling.json.JsonNodeReaderType
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("TooManyFunctions")
internal class JsonNodeReaderAdaptor(
    private val jsonNode: JsonNode,
    private val deserializationContext: DeserializationContext
) : JsonNodeReader {

    private fun JsonNode.toJsonNodeReader() =
        JsonNodeReaderAdaptor(this, deserializationContext)

    private fun JsonNode.toParser() = this.traverse().apply { nextToken() }

    override fun getType() = when (jsonNode.nodeType) {
        JsonNodeType.ARRAY -> JsonNodeReaderType.ARRAY
        JsonNodeType.BOOLEAN -> JsonNodeReaderType.BOOLEAN
        JsonNodeType.NULL -> JsonNodeReaderType.NULL
        JsonNodeType.NUMBER -> JsonNodeReaderType.NUMBER
        JsonNodeType.OBJECT -> JsonNodeReaderType.OBJECT
        JsonNodeType.STRING -> JsonNodeReaderType.STRING
        else -> throw CordaRuntimeException("Error parsing Json: node type ${jsonNode.nodeType} is an internal type unused by Corda")
    }

    override fun isObject() = jsonNode.isObject

    override fun fields() = if (getType() != JsonNodeReaderType.OBJECT) {
        null
    } else {
        jsonNode.fields().asSequence().associate {
            it.key to it.value.toJsonNodeReader()
        }.asIterable().iterator()
    }

    override fun hasField(fieldName: String) = jsonNode.has(fieldName)

    override fun getField(fieldName: String): JsonNodeReader? = jsonNode.get(fieldName)?.toJsonNodeReader()

    override fun isArray() = jsonNode.isArray

    override fun asArray() = if (getType() != JsonNodeReaderType.ARRAY) {
        null
    } else {
        jsonNode.elements().asSequence().map { it.toJsonNodeReader() }.asIterable().iterator()
    }

    override fun isBoolean() = jsonNode.isBoolean

    override fun asBoolean() = jsonNode.asBoolean()

    override fun asBoolean(defaultValue: Boolean) = jsonNode.asBoolean(defaultValue)

    override fun isNumber() = jsonNode.isNumber

    override fun numberValue(): Number? = jsonNode.numberValue()

    override fun isFloatingPointNumber() = jsonNode.isFloatingPointNumber

    override fun isDouble() = jsonNode.isDouble

    override fun doubleValue() = jsonNode.doubleValue()

    override fun asDouble() = jsonNode.asDouble()

    override fun asDouble(defaultValue: Double) = jsonNode.asDouble(defaultValue)

    override fun floatValue() = jsonNode.floatValue()

    override fun isInt() = jsonNode.isInt

    override fun canConvertToInt() = jsonNode.canConvertToInt()

    override fun asInt() = jsonNode.asInt()

    override fun asInt(defaultValue: Int) = jsonNode.asInt(defaultValue)

    override fun canConvertToLong() = jsonNode.canConvertToLong()

    override fun asLong() = jsonNode.asLong()

    override fun asLong(defaultValue: Long) = jsonNode.asLong(defaultValue)

    override fun shortValue() = jsonNode.shortValue()

    override fun bigIntegerValue(): BigInteger = jsonNode.bigIntegerValue()

    override fun bigDecimalValue(): BigDecimal = jsonNode.decimalValue()

    override fun isText() = jsonNode.isTextual

    override fun asText(): String = jsonNode.asText()

    override fun asText(defaultValue: String): String = jsonNode.asText() ?: defaultValue

    override fun binaryValue(): ByteArray? = try {
        jsonNode.binaryValue()
    } catch (e: Exception) {
        null
    }

    override fun isNull() = jsonNode.isNull

    override fun <T> parse(clazz: Class<T>): T? = deserializationContext.readValue(jsonNode.toParser(), clazz)
}

internal fun JsonNodeReaderAdaptor(
    jsonParser: JsonParser,
    deserializationContext: DeserializationContext
): JsonNodeReaderAdaptor {
    val node: JsonNode = jsonParser.codec.readTree<JsonNode>(jsonParser)
    return JsonNodeReaderAdaptor(node, deserializationContext)
}
