package net.corda.rest.server.impl.apigen.processing.openapi.schema.builders

import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaCollectionModel
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaModel
import net.corda.rest.server.impl.apigen.processing.openapi.schema.SchemaModelProvider
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.DataType

internal class SchemaCollectionBuilder(private val schemaModelProvider: SchemaModelProvider) : SchemaBuilder {
    override val keys: List<Class<*>> = listOf(Iterable::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaCollectionModel(
            items = parameterizedClassList.singleOrNull()
                ?.let { schemaModelProvider.toSchemaModel(ParameterizedClass(it.clazz, it.nestedParameterizedTypes)) }
                ?: SchemaModel(type = DataType.OBJECT)
        )
}

internal class SchemaSetBuilder(private val schemaModelProvider: SchemaModelProvider) : SchemaBuilder {
    override val keys: List<Class<*>> = listOf(Set::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaCollectionModel(
            items = parameterizedClassList.singleOrNull()
                ?.let { schemaModelProvider.toSchemaModel(ParameterizedClass(it.clazz, it.nestedParameterizedTypes)) }
                ?: SchemaModel(type = DataType.OBJECT),
            uniqueItems = true
        )
}