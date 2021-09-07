package net.corda.httprpc.server.apigen.processing.openapi.schema.builders

import net.corda.httprpc.server.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.apigen.processing.openapi.schema.model.DataFormat
import net.corda.httprpc.server.apigen.processing.openapi.schema.model.DataType
import net.corda.httprpc.server.apigen.processing.openapi.schema.model.SchemaModel
import net.corda.v5.application.identity.CordaX500Name
import javax.security.auth.x500.X500Principal

class StringSchemaModelBuilder : SchemaBuilder {
    override val keys = listOf(X500Principal::class.java, CordaX500Name::class.java)

    override fun build(clazz: Class<*>, parameterizedClassList: List<GenericParameterizedType>): SchemaModel = SchemaModel(
            type = DataType.STRING,
            format = DataFormat.STRING
    )
}