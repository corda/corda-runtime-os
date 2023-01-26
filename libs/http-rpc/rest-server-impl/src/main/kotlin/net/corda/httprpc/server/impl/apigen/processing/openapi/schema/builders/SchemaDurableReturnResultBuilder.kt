package net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders

import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaDurableReturnResultModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaModel
import net.corda.httprpc.server.impl.apigen.processing.streams.DurableReturnResult
import net.corda.httprpc.server.impl.apigen.processing.streams.FiniteDurableReturnResult
import net.corda.httprpc.server.impl.apigen.processing.toEndpointParameterParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType

internal class SchemaDurableReturnResultBuilder(private val schemaModelProvider: SchemaModelProvider) : SchemaBuilder {
    override val keys: List<Class<*>> = listOf(FiniteDurableReturnResult::class.java, DurableReturnResult::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel {
        val positionedValueType = parameterizedClassList.single()

        return SchemaDurableReturnResultModel(
            clazz.kotlin.memberProperties.filter { it.visibility == KVisibility.PUBLIC }
                .associate {
                    val endpointParameterParameterizedTypes = it.returnType.arguments.mapNotNull { argument ->
                        argument.type?.javaType?.toEndpointParameterParameterizedType()
                    }
                        .run { if (it.name == "positionedValues") toPositionedValuesGenericTypes(positionedValueType) else this }

                    it.name to schemaModelProvider.toSchemaModel(
                        ParameterizedClass(
                            (it.returnType.classifier as? KClass<*>?)?.java ?: Any::class.java,
                            endpointParameterParameterizedTypes,
                            it.returnType.isMarkedNullable
                        )
                    )
                }.toSortedMap()
        )
    }

    private fun List<GenericParameterizedType>.toPositionedValuesGenericTypes(positionedValueType: GenericParameterizedType) =
        this.let { genericParameterizedTypes ->
            if (genericParameterizedTypes.size == 1 && genericParameterizedTypes.single().nestedParameterizedTypes.size == 1) {
                listOf(
                    GenericParameterizedType(
                        genericParameterizedTypes.single().clazz,
                        listOf(positionedValueType)
                    )
                )
            } else genericParameterizedTypes
        }
}