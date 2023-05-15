package net.corda.flow.application.services.impl.interop.facade

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.application.interop.parameters.TypedParameterValue
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class FacadeRequestSerializer : JsonSerializer<FacadeRequest>() {

    override fun serialize(value: FacadeRequest, gen: JsonGenerator, serializers: SerializerProvider) =
        serialize(gen, value.facadeId, value.methodName, value.inParameters);

}

class FacadeResponseSerializer : JsonSerializer<FacadeResponseImpl>() {

    override fun serialize(value: FacadeResponseImpl, gen: JsonGenerator, serializers: SerializerProvider) =
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
    parameters.forEach {
        val parameter = it.parameter
        val parameterValue = it.value
        gen.writeObjectFieldStart(parameter.name)
        gen.writeStringField("type", parameter.type.toString())
        gen.writeFieldName("value")
        parameter.type.writeValue(parameterValue, gen)
        gen.writeEndObject()
    }
    gen.writeEndObject()

    gen.writeEndObject()
}

fun ParameterType<*>.writeValue(value: Any, gen: JsonGenerator): Unit =
    if (this.isQualified) {
        this.rawParameterType.writeValue(value, gen)
    } else {
        when (this.typeLabel) {
            ParameterTypeLabel.BOOLEAN -> gen.writeBoolean(value as Boolean)
            ParameterTypeLabel.STRING -> gen.writeString(value as String)
            ParameterTypeLabel.DECIMAL -> gen.writeNumber(value as BigDecimal)
            ParameterTypeLabel.UUID -> gen.writeString(value.toString())
            ParameterTypeLabel.TIMESTAMP -> gen.writeString((value as ZonedDateTime).format(DateTimeFormatter.ISO_DATE_TIME))
            ParameterTypeLabel.BYTES -> gen.writeString(
                Base64.getEncoder().encodeToString((value as ByteBuffer).array())
            )

            ParameterTypeLabel.JSON ->
                gen.writeTree(
                    gen.codec.readTree(
                        gen.codec.factory.createParser(value as String)
                    )
                )
        }
    }