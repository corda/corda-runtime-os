package net.corda.rest.server.impl.apigen.processing.openapi.schema.builders

import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.rest.server.impl.apigen.processing.openapi.schema.SchemaModelProvider
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaModel
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaPositionedValueModel
import net.corda.rest.server.impl.apigen.processing.toEndpointParameterParameterizedType
import net.corda.rest.durablestream.api.Cursor
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType

internal class SchemaPositionedValueBuilder(private val schemaModelProvider: SchemaModelProvider) : SchemaBuilder {
    override val keys: List<Class<*>> = listOf(Cursor.PollResult.PositionedValue::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel {
        return SchemaPositionedValueModel(
            clazz.kotlin.memberProperties.filter { it.visibility == KVisibility.PUBLIC }
                .associate {
                    if (it.name == "value" && parameterizedClassList.size == 1)
                        it.getPositionedValueObject(parameterizedClassList.single())
                    else it.name to schemaModelProvider.toSchemaModel(
                        ParameterizedClass(
                            (it.returnType.classifier as? KClass<*>?)?.java ?: Any::class.java,
                            it.returnType.arguments.mapNotNull { argument ->
                                argument.type?.javaType?.toEndpointParameterParameterizedType()
                            },
                            it.returnType.isMarkedNullable
                        )

                    )
                }.toSortedMap()
        )
    }

    private fun KProperty1<out Any, *>.getPositionedValueObject(type: GenericParameterizedType) =
        this.name to schemaModelProvider.toSchemaModel(ParameterizedClass(type.clazz, type.nestedParameterizedTypes))
}