package org.corda.weft.facade

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import org.corda.weft.parameters.ParameterType
import org.corda.weft.parameters.TypedParameterValue
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
        serialize(gen, value.facadeId, value.methodName, value.outParameters);

}

private fun serialize(
    gen: JsonGenerator,
    facadeId: FacadeId,
    methodName: String,
    parameters: List<TypedParameterValue<*>>
) {
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

fun ParameterType<*>.writeValue(value: Any, gen: JsonGenerator): Unit = when (this) {
    is ParameterType.BooleanType -> gen.writeBoolean(value as Boolean)
    is ParameterType.StringType -> gen.writeString(value as String)
    is ParameterType.DecimalType -> gen.writeNumber(value as BigDecimal)
    is ParameterType.UUIDType -> gen.writeString(value.toString())
    is ParameterType.TimestampType -> gen.writeString((value as ZonedDateTime).format(DateTimeFormatter.ISO_DATE_TIME))
    is ParameterType.ByteBufferType -> gen.writeString(
        Base64.getEncoder().encodeToString((value as ByteBuffer).array())
    )

    is ParameterType.JsonType ->
        gen.writeTree(
            gen.codec.readTree(
                gen.codec.factory.createParser(value as String)
            )
        )

    is ParameterType.QualifiedType -> type.writeValue(value, gen)
}