package net.corda.rest.server.impl.apigen.processing.openapi.schema.builders

import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.DataFormat
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.DataType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaModel
import net.corda.v5.base.types.MemberX500Name
import javax.security.auth.x500.X500Principal

class StringSchemaModelBuilder : SchemaBuilder {
    override val keys = listOf(X500Principal::class.java, MemberX500Name::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel =
        SchemaModel(
            type = DataType.STRING,
            format = DataFormat.STRING
        )
}