package net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders

import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.DataFormat
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.DataType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaModel
import java.io.InputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.time.temporal.Temporal
import java.time.temporal.TemporalAmount
import java.util.Date
import net.corda.httprpc.HttpFileUpload

internal class SchemaBooleanBuilder : SchemaBuilder {
    override val keys = listOf(Boolean::class.java, Boolean::class.javaObjectType)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.BOOLEAN
        )
}

internal class SchemaIntegerBuilder : SchemaBuilder {
    override val keys = listOf(Int::class.java, Int::class.javaObjectType)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.INTEGER,
            DataFormat.INT32
        )
}

internal class SchemaLongBuilder : SchemaBuilder {
    override val keys = listOf(Long::class.java, Long::class.javaObjectType)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.INTEGER,
            DataFormat.INT64
        )
}

internal class SchemaBigIntegerBuilder : SchemaBuilder {
    override val keys = listOf(BigInteger::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.INTEGER
        )
}

internal class SchemaFloatBuilder : SchemaBuilder {
    override val keys = listOf(Float::class.java, Float::class.javaObjectType)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.NUMBER,
            DataFormat.FLOAT
        )
}

internal class SchemaDoubleBuilder : SchemaBuilder {
    override val keys = listOf(Double::class.java, Double::class.javaObjectType)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.NUMBER,
            DataFormat.DOUBLE
        )
}

internal class SchemaBigDecimalBuilder : SchemaBuilder {
    override val keys = listOf(BigDecimal::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.NUMBER
        )
}

internal class SchemaStringBuilder : SchemaBuilder {
    override val keys = listOf(String::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.STRING
        )
}

internal class SchemaByteArrayBuilder : SchemaBuilder {
    override val keys = listOf(
        ByteArray::class.java, ByteArray::class.javaObjectType,
        Array<Byte>::class.java, Array<Byte>::class.javaObjectType
    )

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.STRING,
            DataFormat.BYTE
        )
}

internal class SchemaHttpFileUploadBuilder : SchemaBuilder {
    override val keys = listOf(HttpFileUpload::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.STRING,
            DataFormat.BINARY
        ).apply { description = "A content of the file to upload." }
}

internal class SchemaInputStreamBuilder : SchemaBuilder {
    override val keys = listOf(InputStream::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.STRING,
            DataFormat.BINARY
        ).apply { description = "A content of the file to upload." }
}

internal class SchemaDateBuilder : SchemaBuilder {
    override val keys = listOf(Date::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.STRING,
            DataFormat.DATETIME
        )
}

internal class SchemaDateTimeBuilder : SchemaBuilder {
    override val keys = listOf(Temporal::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.STRING,
            DataFormat.DATETIME
        )
}

internal class SchemaDurationBuilder : SchemaBuilder {
    override val keys = listOf(TemporalAmount::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            DataType.STRING,
            DataFormat.DURATION
        )
}