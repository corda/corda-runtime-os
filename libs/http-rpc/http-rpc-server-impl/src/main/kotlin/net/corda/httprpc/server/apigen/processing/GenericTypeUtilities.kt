package net.corda.httprpc.server.apigen.processing

import net.corda.httprpc.server.apigen.models.GenericParameterizedType
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure

private val log = LoggerFactory.getLogger("net.corda.httprpc.server.apigen.processing.GenericTypeUtilities.kt")

fun KParameter.getParameterizedTypes(): List<GenericParameterizedType> {
    return when (val type = this.type.jvmErasure.java) {
        is ParameterizedType -> type.actualTypeArguments.mapNotNull { it.toEndpointParameterParameterizedType() }
        else -> emptyList()
    }
}

fun Type.toEndpointParameterParameterizedType(): GenericParameterizedType? {
    log.trace { """Map type: "${this.typeName}" to GenericParameterizedType.""" }
    return when (this) {
        is Class<*> -> GenericParameterizedType(this)
        is ParameterizedType -> {
            GenericParameterizedType(
                this.rawType as Class<*>,
                this.actualTypeArguments.mapNotNull { it.toEndpointParameterParameterizedType() })
        }
        is WildcardType -> {
            this.upperBounds.singleOrNull()?.toEndpointParameterParameterizedType()
        }
        else -> {
            GenericParameterizedType(Any::class.java)
        }
    }.also { log.trace { """Map type: "${this.typeName}" to GenericParameterizedType completed.""" } }
}

fun Method.toClassAndParameterizedTypes(): Pair<Class<*>, List<GenericParameterizedType>> {
    log.trace { """Map method: "${this.name}" to Class and ParameterizedTypes.""" }
    val topLevelGenericType = this.genericReturnType.toEndpointParameterParameterizedType()
    val clazz = topLevelGenericType?.clazz ?: this.returnType
    val parameterizedTypes = topLevelGenericType?.nestedParameterizedTypes ?: emptyList()
    return (clazz to parameterizedTypes)
        .also { log.trace { """Map method: "${this.name}" to Class and ParameterizedTypes completed.""" } }
}
