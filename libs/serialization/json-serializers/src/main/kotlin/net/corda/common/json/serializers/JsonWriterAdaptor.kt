package net.corda.common.json.serializers

import com.fasterxml.jackson.core.Base64Variants
import com.fasterxml.jackson.core.JsonGenerator
import net.corda.v5.application.marshalling.json.JsonSerializedBase64Config
import net.corda.v5.application.marshalling.json.JsonWriter
import java.io.InputStream
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Adaptor between the Jackson [JsonGenerator] and Corda's own [JsonWriter]. Provides the public api with the means of
 * writing Json without exposing Jackson to the public api.
 */
@Suppress("TooManyFunctions")
internal class JsonWriterAdaptor(private val jsonGenerator: JsonGenerator) : JsonWriter {

    private fun jacksonConfigFor(config: JsonSerializedBase64Config) = when (config) {
        JsonSerializedBase64Config.MIME -> Base64Variants.MIME
        JsonSerializedBase64Config.MIME_NO_LINEFEEDS -> Base64Variants.MIME_NO_LINEFEEDS
        JsonSerializedBase64Config.MODIFIED_FOR_URL -> Base64Variants.MODIFIED_FOR_URL
        JsonSerializedBase64Config.PEM -> Base64Variants.PEM
    }

    override fun writeStartObject() {
        jsonGenerator.writeStartObject()
    }

    override fun writeEndObject() {
        jsonGenerator.writeEndObject()
    }

    override fun writeFieldName(fieldName: String) {
        jsonGenerator.writeFieldName(fieldName)
    }

    override fun writeString(c: CharArray, offset: Int, len: Int) {
        jsonGenerator.writeString(c, offset, len)
    }

    override fun writeString(text: String) {
        jsonGenerator.writeString(text)
    }

    override fun writeStringField(fieldName: String, text: String) {
        jsonGenerator.writeStringField(fieldName, text)
    }

    override fun writeNumber(v: BigDecimal) {
        jsonGenerator.writeNumber(v)
    }

    override fun writeNumber(v: BigInteger) {
        jsonGenerator.writeNumber(v)
    }

    override fun writeNumber(v: Double) {
        jsonGenerator.writeNumber(v)
    }

    override fun writeNumber(v: Float) {
        jsonGenerator.writeNumber(v)
    }

    override fun writeNumber(v: Int) {
        jsonGenerator.writeNumber(v)
    }

    override fun writeNumber(v: Long) {
        jsonGenerator.writeNumber(v)
    }

    override fun writeNumber(v: Short) {
        jsonGenerator.writeNumber(v)
    }

    override fun writeNumberField(fieldName: String, v: BigDecimal) {
        jsonGenerator.writeNumberField(fieldName, v)
    }

    override fun writeNumberField(fieldName: String, v: Double) {
        jsonGenerator.writeNumberField(fieldName, v)
    }

    override fun writeNumberField(fieldName: String, v: Float) {
        jsonGenerator.writeNumberField(fieldName, v)
    }

    override fun writeNumberField(fieldName: String, v: Int) {
        jsonGenerator.writeNumberField(fieldName, v)
    }

    override fun writeNumberField(fieldName: String, v: Long) {
        jsonGenerator.writeNumberField(fieldName, v)
    }

    override fun writeObject(pojo: Any) {
        jsonGenerator.writeObject(pojo)
    }

    override fun writeObjectField(fieldName: String, pojo: Any) {
        jsonGenerator.writeObjectField(fieldName, pojo)
    }

    override fun writeObjectFieldStart(fieldName: String) {
        jsonGenerator.writeObjectFieldStart(fieldName)
    }

    override fun writeBoolean(state: Boolean) {
        jsonGenerator.writeBoolean(state)
    }

    override fun writeBooleanField(fieldName: String, state: Boolean) {
        jsonGenerator.writeBooleanField(fieldName, state)
    }

    override fun writeArrayFieldStart(fieldName: String) {
        jsonGenerator.writeArrayFieldStart(fieldName)
    }

    override fun writeStartArray() {
        jsonGenerator.writeStartArray()
    }

    override fun writeEndArray() {
        jsonGenerator.writeEndArray()
    }

    override fun writeArray(array: IntArray, offset: Int, len: Int) {
        jsonGenerator.writeArray(array, offset, len)
    }

    override fun writeArray(array: LongArray, offset: Int, len: Int) {
        jsonGenerator.writeArray(array, offset, len)
    }

    override fun writeArray(array: DoubleArray, offset: Int, len: Int) {
        jsonGenerator.writeArray(array, offset, len)
    }

    override fun writeArray(array: Array<String>, offset: Int, len: Int) {
        jsonGenerator.writeArray(array, offset, len)
    }

    override fun writeBinary(config: JsonSerializedBase64Config, data: ByteArray, offset: Int, len: Int) {
        jsonGenerator.writeBinary(jacksonConfigFor(config), data, offset, len)
    }

    override fun writeBinary(config: JsonSerializedBase64Config, data: InputStream, len: Int) {
        jsonGenerator.writeBinary(jacksonConfigFor(config), data, len)
    }

    override fun writeBinary(data: ByteArray) {
        jsonGenerator.writeBinary(data)
    }

    override fun writeBinary(data: ByteArray, offset: Int, len: Int) {
        jsonGenerator.writeBinary(data, offset, len)
    }

    override fun writeBinary(data: InputStream, len: Int) {
        jsonGenerator.writeBinary(data, len)
    }

    override fun writeBinaryField(fieldName: String, data: ByteArray) {
        jsonGenerator.writeBinaryField(fieldName, data)
    }

    override fun writeNull() {
        jsonGenerator.writeNull()
    }

    override fun writeNullField(fieldName: String) {
        jsonGenerator.writeNullField(fieldName)
    }

    override fun writeRaw(c: Char) {
        jsonGenerator.writeRaw(c)
    }

    override fun writeRaw(c: CharArray, offset: Int, len: Int) {
        jsonGenerator.writeRaw(c, offset, len)
    }

    override fun writeRaw(text: String) {
        jsonGenerator.writeRaw(text)
    }

    override fun writeRaw(text: String, offset: Int, len: Int) {
        jsonGenerator.writeRaw(text, offset, len)
    }

    override fun writeRawValue(c: CharArray, offset: Int, len: Int) {
        jsonGenerator.writeRaw(c, offset, len)
    }

    override fun writeRawValue(text: String) {
        jsonGenerator.writeRawValue(text)
    }

    override fun writeRawValue(text: String, offset: Int, len: Int) {
        jsonGenerator.writeRawValue(text, offset, len)
    }
}
