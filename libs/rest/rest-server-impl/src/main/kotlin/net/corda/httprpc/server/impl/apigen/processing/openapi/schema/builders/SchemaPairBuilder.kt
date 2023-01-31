package net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders

import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaPairModel
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

internal class SchemaPairBuilder(private val schemaModelProvider: SchemaModelProvider) : SchemaBuilder {
    override val keys: List<Class<*>> = listOf(Pair::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel {
        val canDeduceTypesFromParameterizedClassList = parameterizedClassList.size == 2
        val iterator = parameterizedClassList.iterator()
        // schema pair model is necessary because the Pair::class does not give enough information
        // to replace the relevant SchemaObjectModel with a ref.
        // however, all pairs may differ due to their contained types, so we evaluate them all without referencing
        return SchemaPairModel(
            clazz.kotlin.memberProperties.filter { it.visibility == KVisibility.PUBLIC }
                .associate {
                    val nextValue = if (canDeduceTypesFromParameterizedClassList) iterator.next() else null
                    it.name to schemaModelProvider.toSchemaModel(
                        ParameterizedClass(
                            nextValue?.clazz ?: Any::class.java,
                            nextValue?.nestedParameterizedTypes ?: emptyList(),
                            it.returnType.isMarkedNullable
                        )

                    )
                }
        )
    }
}