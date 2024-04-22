package net.corda.blobinspector

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import org.apache.qpid.proton.amqp.Binary
import java.io.StringWriter

fun Any?.prettyPrint(): String? {
    return this?.let {
        val sw = StringWriter()
        val jg = JsonFactory().createGenerator(sw).useDefaultPrettyPrinter()
        convertToJson(it, jg)
        jg.close()
        return sw.toString()
    }
}

fun convertToJson(it: Any?, jg: JsonGenerator) {
    if (it == null) {
        jg.writeNull()
    } else if (it is List<*>) {
        jg.writeStartArray()
        it.forEach { element -> convertToJson(element, jg) }
        jg.writeEndArray()
    } else if (it is ByteArray) {
        jg.writeBinary(it)
    } else if (it is Array<*>) {
        jg.writeStartArray()
        it.forEach { element -> convertToJson(element, jg) }
        jg.writeEndArray()
    } else if (it is Map<*, *>) {
        @Suppress("ComplexCondition")
        if (it.containsKey("_class") || it.containsKey("_custom") || it.containsKey("_predefined") || it.containsKey("_bytes")) {
            // Object
            jg.writeStartObject()
            it.forEach { key, value ->
                jg.writeFieldName(key.toString())
                convertToJson(value, jg)
            }
            jg.writeEndObject()
        } else {
            // Map
            jg.writeStartArray()
            it.forEach { key, value ->
                jg.writeStartObject()
                jg.writeFieldName("key")
                convertToJson(key, jg)
                jg.writeFieldName("value")
                convertToJson(value, jg)
                jg.writeEndObject()
            }
            jg.writeEndArray()
        }
    } else if (it is Binary) {
        jg.writeBinary(it.array)
    } else if (it is ByteSequence) {
        jg.writeBinary(it.bytes)
    } else if (it is Number) {
        jg.writeNumber(it.toString())
    } else {
        jg.writeString(it.toString())
    }
}
