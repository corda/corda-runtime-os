package net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders

import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.DataFormat
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.DataType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaModel
import java.util.UUID

internal class SchemaUUIDBuilder : SchemaBuilder {
    override val keys: List<Class<*>> = listOf(UUID::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            type = DataType.STRING,
            format = DataFormat.UUID
        )
}