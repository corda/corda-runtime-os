package net.corda.httprpc.server.impl.apigen.processing.openapi.schema

import net.corda.httprpc.server.impl.apigen.models.EndpointParameter
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.JsonSchemaBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.HttpResponseTypeBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaBigDecimalBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaBigIntegerBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaBooleanBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaByteArrayBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaCollectionBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaDateBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaDateTimeBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaDoubleBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaDurableReturnResultBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaDurationBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaEnumBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaFloatBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaHttpFileUploadBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaInputStreamBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaIntegerBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaLongBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaMapBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaObjectBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaPairBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaPositionedValueBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaSetBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaStringBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.SchemaUUIDBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders.StringSchemaModelBuilder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaMultiRefObjectModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaObjectModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaRefObjectModel
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

private val log =
    LoggerFactory.getLogger("net.corda.httprpc.server.impl.SchemaModelProvider.kt")

/**
 * [SchemaModelProvider] is responsible for providing a [SchemaModel] from the passed argument(s).
 *
 */
internal interface SchemaModelProvider {
    fun toSchemaModel(param: EndpointParameter): SchemaModel
    fun toSchemaModel(parameterizedClass: ParameterizedClass): SchemaModel
    fun toSchemaModel(properties: List<EndpointParameter>, schemaModelName: String): SchemaModel
}

/**
 * [DefaultSchemaModelProvider] is implementing the [SchemaModelProvider]
 * and uses a collection of potential builders
 * and the [SchemaModelContextHolder] in order to generate the requested model.
 *
 * If the object returned from the builder is a [SchemaObjectModel],
 * then it is a candidate for referencing using [SchemaModelContextHolder].
 * Thus, it is not advised to use the [SchemaObjectBuilder] for generic objects where the generic types need to be represented.
 */
internal class DefaultSchemaModelProvider(private val schemaModelContextHolder: SchemaModelContextHolder) :
    SchemaModelProvider {
    private val defaultBuilder = SchemaObjectBuilder(this, schemaModelContextHolder)

    private val builders = listOf(
        SchemaSetBuilder(this),
        SchemaCollectionBuilder(this),
        SchemaMapBuilder(this),
        SchemaBooleanBuilder(),
        SchemaIntegerBuilder(),
        SchemaLongBuilder(),
        SchemaBigIntegerBuilder(),
        SchemaFloatBuilder(),
        SchemaDoubleBuilder(),
        SchemaBigDecimalBuilder(),
        SchemaStringBuilder(),
        SchemaByteArrayBuilder(),
        SchemaHttpFileUploadBuilder(),
        SchemaInputStreamBuilder(),
        SchemaDateBuilder(),
        SchemaDateTimeBuilder(),
        SchemaEnumBuilder(),
        SchemaUUIDBuilder(),
        SchemaDurationBuilder(),
        StringSchemaModelBuilder(),
        SchemaPairBuilder(this),
        SchemaDurableReturnResultBuilder(this),
        SchemaPositionedValueBuilder(this),
        JsonSchemaBuilder(),
        HttpResponseTypeBuilder(this)
    )

    override fun toSchemaModel(properties: List<EndpointParameter>, schemaModelName: String): SchemaModel {
        val schemaRefObjectModel: SchemaRefObjectModel
        log.debug { """To schema model "$schemaModelName" from endpointParameters: "${properties.joinToString(",")}".""" }
        properties.associateBy({ it.id }, { toSchemaModel(it) }).toSortedMap().also { schemaRefObjectModels ->
            schemaRefObjectModel = SchemaMultiRefObjectModel(ref = schemaModelName, properties = schemaRefObjectModels)
                .also {
                    it.name = schemaModelName
                    it.description = schemaModelName
                    it.required = properties.any { p -> p.required }
                    it.nullable = properties.all { p -> p.nullable }
                }
        }
        schemaModelContextHolder.addRefObjectWrapperSchema(schemaModelName, schemaRefObjectModel)
        return SchemaRefObjectModel(ref = schemaModelName).also {
            log.debug { """To schema model "$schemaModelName" from endpointParameter(s) completed.""" }
        }
    }

    override fun toSchemaModel(param: EndpointParameter): SchemaModel {
        log.debug { """To schema model from endpointParameter: "$param".""" }
        val parameterizedClass = ParameterizedClass(param.classType, param.parameterizedTypes, param.nullable)
            .apply {
                required = param.required
                name = param.name
                description = param.description
            }
        return getExistingSchemaRefOrNull(parameterizedClass)
            ?: buildAndRegisterNewSchema(parameterizedClass)
    }

    override fun toSchemaModel(parameterizedClass: ParameterizedClass): SchemaModel {
        log.debug { """To schema model for class: "$parameterizedClass".""" }
        return getExistingSchemaRefOrNull(parameterizedClass)
            ?: buildAndRegisterNewSchema(parameterizedClass)
    }

    private fun buildAndRegisterNewSchema(parameterizedClass: ParameterizedClass): SchemaModel {
        return getBuilderFor(parameterizedClass.clazz)
            .build(parameterizedClass.clazz, parameterizedClass.parameterizedClassList)
            .apply {
                if (this !is SchemaObjectModel) this.example = parameterizedClass.clazz.toExample()
                if(!parameterizedClass.name.isNullOrBlank()) name = parameterizedClass.name
                if(!parameterizedClass.description.isNullOrBlank()) description = parameterizedClass.description
            }.let {
                returnOrRegisterAndReturnRef(it, parameterizedClass)
            }.also {
                // apply nullable and required to the returned model. It won't be a SchemaObjectModel.
                it.required = parameterizedClass.required
                it.nullable = parameterizedClass.nullable
                log.debug { """To schema model for class: "$parameterizedClass" completed.""" }
            }
    }

    private fun getExistingSchemaRefOrNull(parameterizedClass: ParameterizedClass) : SchemaRefObjectModel? {
        return schemaModelContextHolder.getSchema(parameterizedClass)?.let {
            SchemaRefObjectModel(ref = schemaModelContextHolder.getName(parameterizedClass)!!)
        }?.also {
            // we set required and nullable on a reference to an object in the schema
            it.required = parameterizedClass.required
            it.nullable = parameterizedClass.nullable
            log.debug { """Schema for class: "$parameterizedClass" found in context. To schema model completed.""" }
        }
    }

    private fun getBuilderFor(clazz: Class<*>): SchemaBuilder {
        log.trace { """Get builder for "$clazz".""" }
        return builders.firstOrNull { it.isSupertypeOf(clazz) }
            ?.also { log.trace { """Get builder for "$clazz" completed. Builder: "$it".""" } }
            ?: defaultBuilder.also { log.trace { """Get builder for "$clazz" completed. Default builder used: $it""" } }
    }

    private fun returnOrRegisterAndReturnRef(model: SchemaModel, parameterizedClass: ParameterizedClass): SchemaModel {
        log.trace { """Return or register and return ref for class: "$parameterizedClass.clazz", model: "$model".""" }
        return when (model) {
            is SchemaObjectModel -> {
                log.trace { "SchemaObjectModel found, registering and returning ref." }
                schemaModelContextHolder.add(parameterizedClass, model)
                SchemaRefObjectModel(ref = schemaModelContextHolder.getName(parameterizedClass)!!)
            }
            else -> model
        }.also {
            log.trace { """Return or register and return ref for class: "${parameterizedClass.clazz}", 
                |model: "$model", returned model: "$it" completed.""".trimMargin() }
        }
    }
}