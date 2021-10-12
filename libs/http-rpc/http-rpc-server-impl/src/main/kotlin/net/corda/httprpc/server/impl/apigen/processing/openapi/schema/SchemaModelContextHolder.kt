package net.corda.httprpc.server.impl.apigen.processing.openapi.schema

import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaObjectModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaRefObjectModel
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory

private val log =
    LoggerFactory.getLogger("net.corda.httprpc.server.impl.SchemaModelContextHolder.kt")

/**
 * [SchemaModelContextHolder] is responsible for keeping track of discovered schemas,
 * in order to use them for generating reference schema models or for graph traversal dead-ends.
 *
 * It is a singleton object.
 */

@Suppress("UNCHECKED_CAST")
class SchemaModelContextHolder {

    private val pClass2Name = mutableMapOf<ParameterizedClass, String>()
    private val pClass2Model = mutableMapOf<ParameterizedClass, SchemaObjectModel>()
    private val propertyWrapperModels = mutableMapOf<String, SchemaRefObjectModel>()

    internal fun add(parameterizedClass: ParameterizedClass, schema: SchemaObjectModel) {
        log.trace { """Add schema: "$schema" to class "$parameterizedClass".""" }
        pClass2Model[parameterizedClass] = schema
        markDiscovered(parameterizedClass)
        log.trace { """Add schema: "$schema" to class "$parameterizedClass" completed.""" }
    }

    internal fun addRefObjectWrapperSchema(name: String, schema: SchemaRefObjectModel) {
        propertyWrapperModels[name] = schema
    }

    private fun generateName(parameterizedClass: ParameterizedClass): String {
        log.trace { """Generate name for class "$parameterizedClass".""" }
        val classSimpleName = parameterizedClass.clazz.simpleName
        val existingClassNameInDifferentPackagesCount =
            pClass2Name.keys.count { it.clazz != parameterizedClass.clazz && it.clazz.simpleName == classSimpleName }

        return (if (existingClassNameInDifferentPackagesCount == 0) classSimpleName
        else "${classSimpleName}_$existingClassNameInDifferentPackagesCount") +
                parameterizedClass.parameterizedClassList.mapKey
                    .also { log.trace { """Generate name for class "$parameterizedClass".""" } }
    }

    internal fun getName(parameterizedClass: ParameterizedClass): String? {
        log.trace { """Get name for "$parameterizedClass".""" }
        return pClass2Name[parameterizedClass]
            .also { log.trace { """Get name for "$parameterizedClass" completed.""" } }
    }

    internal fun getSchema(parameterizedClass: ParameterizedClass): SchemaObjectModel? {
        log.trace { """Get schema for "$parameterizedClass".""" }
        return pClass2Model[parameterizedClass]
            .also { log.trace { """Get schema for "$parameterizedClass" completed.""" } }
    }

    internal fun getAllSchemas(): Map<String, SchemaObjectModel> {

        log.trace { "Get all schemas." }
        return pClass2Model.map { pClass2Name[it.key]!! to it.value }.toMap().plus(propertyWrapperModels)
            .also { log.trace { """Get all schemas, size: "${it.size}", completed.""" } } as Map<String, SchemaObjectModel>
    }

    internal fun markDiscovered(parameterizedClass: ParameterizedClass) {
        log.trace { """Mark class discovered "$parameterizedClass".""" }
        if (!pClass2Name.containsKey(parameterizedClass)) {
            pClass2Name[parameterizedClass] = generateName(parameterizedClass)
        }
        log.trace { """Mark class discovered "$parameterizedClass" completed.""" }
    }

}