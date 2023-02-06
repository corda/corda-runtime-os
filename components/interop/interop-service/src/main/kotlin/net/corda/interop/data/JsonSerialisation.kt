package net.corda.interop.data

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class FacadeRequestSerializer : JsonSerializer<FacadeRequest>() {

    override fun serialize(value: FacadeRequest, gen: JsonGenerator, serializers: SerializerProvider) =
        serialize(gen, value.facadeId, value.methodName, value.inParameters);
    
}

class FacadeResponseSerializer : JsonSerializer<FacadeResponse>() {

    override fun serialize(value: FacadeResponse, gen: JsonGenerator, serializers: SerializerProvider) =
        serialize(gen, value.facadeId, value.methodName, value.outParameters)

}

private fun serialize(gen: JsonGenerator,
                      facadeId: FacadeId,
                      methodName: String,
                      parameters: List<FacadeParameterValue<*>>) {
    gen.writeStartObject()

    gen.writeStringField("method", "$facadeId/$methodName")

    gen.writeObjectFieldStart("parameters")
    parameters.forEach { (parameter, parameterValue) ->
        gen.writeObjectFieldStart(parameter.name)
        gen.writeStringField("type", parameter.type.toString())
        gen.writeFieldName("value")
        parameter.type.writeValue(parameterValue, gen)
        gen.writeEndObject()
    }
    gen.writeEndObject()

    gen.writeEndObject()
}

fun FacadeParameterType<*>.writeValue(value: Any, gen: JsonGenerator): Unit = when(this) {
    is FacadeParameterType.BooleanType -> gen.writeBoolean(value as Boolean)
    is FacadeParameterType.StringType -> gen.writeString(value as String)
    is FacadeParameterType.DecimalType -> gen.writeNumber(value as BigDecimal)
    is FacadeParameterType.UUIDType -> gen.writeString(value.toString())
    is FacadeParameterType.TimestampType -> gen.writeString((value as ZonedDateTime).format(DateTimeFormatter.ISO_DATE_TIME))
    is FacadeParameterType.ByteBufferType -> gen.writeString(Base64.getEncoder().encodeToString((value as ByteBuffer).array()))
    is FacadeParameterType.JsonType ->
        gen.writeTree(
            gen.codec.readTree(
                gen.codec.factory.createParser(value as String)))
    is FacadeParameterType.QualifiedType -> type.writeValue(value, gen)
}