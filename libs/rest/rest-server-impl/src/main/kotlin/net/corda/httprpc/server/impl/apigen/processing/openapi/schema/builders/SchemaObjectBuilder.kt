package net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders

import com.fasterxml.jackson.annotation.JsonIgnore
import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelContextHolder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaObjectModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaRefObjectModel
import net.corda.httprpc.server.impl.apigen.processing.toEndpointParameterParameterizedType
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaType

internal class SchemaObjectBuilder(
    private val schemaModelProvider: SchemaModelProvider,
    private val schemaModelContextHolder: SchemaModelContextHolder
) : SchemaBuilder {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keys: List<Class<*>> = listOf(Any::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel {
        // if the ref name is discovered, then this class will eventually be resolved and this ref is safe to refer to
        // this also stops infinite recursions, as the nested same type is resolved immediately
        schemaModelContextHolder.getName(ParameterizedClass(clazz, parameterizedClassList))?.let {
            return SchemaRefObjectModel(ref = it)
        }

        schemaModelContextHolder.markDiscovered(ParameterizedClass(clazz, parameterizedClassList))
        try {
            return SchemaObjectModel(
                // retransforming to kotlin, because otherwise @JvmField is needed to have these fields exposed to java reflection
                // and we don't want to force users to add more annotations :)
                clazz.kotlin.memberProperties.filter {
                    it.visibility == KVisibility.PUBLIC &&
                            it.annotations.none { annotation -> annotation is JsonIgnore } &&
                            // annotations targeting ElementType.FIELD need to be resolved from javaField instead
                            // however, it doesn't hurt to check kotlin property annotations too as above
                            (it.javaField?.annotations?.none { annotation -> annotation is JsonIgnore } ?: true)

                }.associate {
                    it.name to schemaModelProvider.toSchemaModel(
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
        } catch (th: Throwable) {
            log.error("Cannot create SchemaObjectModel for: $clazz with $parameterizedClassList", th)
            throw th
        }
    }
}