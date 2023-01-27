package net.corda.httprpc.server.impl.apigen.processing.openapi.schema

import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.BooleanSchema
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.NumberSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.JsonSchemaModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaCollectionModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaDurableReturnResultModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaEnumModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaMapModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaModelFieldsHelper
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaMultiRefObjectModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaObjectModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaPairModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaPositionedValueModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaRefObjectModel
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory

private val log =
    LoggerFactory.getLogger("net.corda.httprpc.server.impl.SchemaModelToOpenApiSchemaConverter.kt")

/**
 * [SchemaModelToOpenApiSchemaConverter] is a conversion layer between our [SchemaModel] and swagger's required [Schema].
 */
object SchemaModelToOpenApiSchemaConverter {
    @Suppress("ComplexMethod")
    fun convert(schemaModel: SchemaModel): Schema<Any> {
        log.trace { """Convert schemaModel "$schemaModel" to schema.""" }
        return when (schemaModel) {
            is SchemaEnumModel -> convertBaseSchemaModel(schemaModel).apply {
                schemaModel.enum?.forEach { this.addEnumItemObject(it) }
            }
            is SchemaCollectionModel -> ArraySchema().apply {
                schemaModel.type?.let { this.type(it.toString().lowercase()) }
                schemaModel.items?.let { this.items(convert(it)) }
                this.uniqueItems(schemaModel.uniqueItems)
            }
            is SchemaMapModel -> convertBaseSchemaModel(schemaModel).apply {
                schemaModel.additionalProperties?.let { props -> this.additionalProperties(convert(props)) }
            }
            is SchemaObjectModel -> convertBaseSchemaModel(schemaModel).apply {
                this.properties(schemaModel.properties.mapValues { convert(it.value) })
            }
            is SchemaPairModel -> convertBaseSchemaModel(schemaModel).apply {
                this.properties(schemaModel.properties.mapValues { convert(it.value) })
            }
            is SchemaDurableReturnResultModel -> convertBaseSchemaModel(schemaModel).apply {
                this.properties(schemaModel.properties.mapValues { convert(it.value) })
            }
            is SchemaPositionedValueModel -> convertBaseSchemaModel(schemaModel).apply {
                this.properties(schemaModel.properties.mapValues { convert(it.value) })
            }
            is SchemaMultiRefObjectModel -> convertBaseSchemaModel(schemaModel).apply {
                this.properties(schemaModel.properties.mapValues { convert(it.value) })
            }
            // extraordinary case, where the object's ref is expected to be found in the overall structure
            is SchemaRefObjectModel -> Schema<Any>().apply {
                `$ref`(schemaModel.ref)
            }
            is JsonSchemaModel -> ComposedSchema().apply {
                description = "Can be any value - string, number, boolean, array or object."
                example = "{\"command\":\"echo\", \"data\":{\"value\": \"hello-world\"}}"
                anyOf(
                    listOf(
                        StringSchema(),
                        NumberSchema(),
                        IntegerSchema(),
                        BooleanSchema(),
                        ArraySchema(),
                        ObjectSchema(),
                    )
                )
            }
            else -> convertBaseSchemaModel(schemaModel)

        }.also {
            it.setRequiredAndNullable(schemaModel)
            log.trace { """Convert schemaModel "$schemaModel" to schema, Result: "$it" completed.""" }
        }
    }

    private fun convertBaseSchemaModel(schemaModel: SchemaModel) = Schema<Any>().apply {
        schemaModel.format?.let { this.format(it.toString().lowercase()) }
        schemaModel.type?.let { this.type(it.toString().lowercase()) }
        schemaModel.name?.let { this.name(it) }
        schemaModel.description?.let { this.description(it) }
        schemaModel.example?.let { this.example(it) }
    }

    private fun Schema<Any>.setRequiredAndNullable(schemaModel: SchemaModel) {
        this.required = if (schemaModel is SchemaModelFieldsHelper) schemaModel.getRequiredFields() else emptyList()
        this.nullable = schemaModel.nullable
    }
}
