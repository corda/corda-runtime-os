package net.corda.rest.server.impl.apigen.processing.openapi.schema.builders

import net.corda.rest.response.ResponseEntity
import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.rest.server.impl.apigen.processing.openapi.schema.SchemaModelProvider
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaModel

internal class HttpResponseTypeBuilder(
    private val schemaModelProvider: SchemaModelProvider
) : SchemaBuilder {
    override val keys = listOf(ResponseEntity::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel {
        // HttpResponse only has one generic type. Get this type and build schema model from it.
        val realType = parameterizedClassList.first()
        return schemaModelProvider.toSchemaModel(
            ParameterizedClass(
                realType.clazz,
                realType.nestedParameterizedTypes
            )
        )
    }
}