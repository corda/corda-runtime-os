package net.corda.rest.server.impl.apigen.processing.openapi.schema.model

/**
 * [SchemaModel] is the base class for all schema models, following a similar structure to that of the Swagger's Schema.
 */
open class SchemaModel(
    val type: DataType? = null,
    val format: DataFormat? = null
) {
    var name: String? = null
    var description: String? = null
    var example: Any? = null
    var required: Boolean = true
    // defaults to null so we can avoid setting in object schema
    var nullable: Boolean? = null
}

interface SchemaModelFieldsHelper {
    fun getRequiredFields(): List<String>
}

open class SchemaEnumModel(
    val enum: List<String>? = null
) : SchemaModel()

open class SchemaCollectionModel(
    val items: SchemaModel?,
    val uniqueItems: Boolean = false
) : SchemaModel(type = DataType.ARRAY)

open class SchemaMapModel(
    val additionalProperties: SchemaModel?
) : SchemaModel(type = DataType.OBJECT)

open class SchemaObjectModel(
    val properties: Map<String, SchemaModel> = emptyMap()
) : SchemaModel(type = DataType.OBJECT), SchemaModelFieldsHelper {
    override fun getRequiredFields() = this.properties.filter { it.value.isRequiredField() }.keys.toList().sorted()
}

open class SchemaPairModel(
    val properties: Map<String, SchemaModel> = emptyMap()
) : SchemaModel(type = DataType.OBJECT)

open class SchemaDurableReturnResultModel(
    properties: Map<String, SchemaModel> = emptyMap()
) : SchemaObjectModel(properties)

open class SchemaPositionedValueModel(
    val properties: Map<String, SchemaModel> = emptyMap()
) : SchemaModel(type = DataType.OBJECT)

open class SchemaRefObjectModel(
    val ref: String
) : SchemaModel(), SchemaModelFieldsHelper {
    override fun getRequiredFields() = listOf(this.ref)
}

open class SchemaMultiRefObjectModel(
    val properties: Map<String, SchemaModel> = emptyMap(), ref: String
) : SchemaRefObjectModel(ref) {
    override fun getRequiredFields() = this.properties.filter { it.value.isRequiredField() }.keys.toList().sorted()
}

/**
 * JsonSchemaModel is a special type that allows all JSON types and null format.
 */
open class JsonSchemaModel : SchemaModel(
    type = null,
    format = null,
)

private fun SchemaModel.isRequiredField() = required && notNullableOrNull()
private fun SchemaModel.notNullableOrNull() = (nullable == null || nullable == false)