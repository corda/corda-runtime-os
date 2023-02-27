package net.corda.httprpc.client.processing

import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.annotations.ClientRequestBodyParameter
import net.corda.httprpc.client.serialization.objectMapper
import net.corda.httprpc.tools.annotations.extensions.name
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.lang.reflect.Method

private val log = LoggerFactory.getLogger("net.corda.httprpc.client.internal.processing.BodyParametersResolver.kt")

internal fun Method.bodyParametersFrom(methodArguments: Array<out Any?>, extraParameters: Map<String, Any?> = emptyMap()): String {
    log.trace { """Method body parameters from "$methodArguments".""" }
    val bodyParameters = this.parameters
        .mapIndexed { index, parameter -> parameter to methodArguments[index] }
        .filter {
            it.first.annotations.any { annotation -> annotation is ClientRequestBodyParameter } ||
                    it.first.annotations.none { annotation -> annotation is RestPathParameter || annotation is RestQueryParameter }
        }

    val bodyParametersByName = bodyParameters.map {
        if (it.first.annotations.any { annotation -> annotation is ClientRequestBodyParameter }) {
            it.first.getAnnotation(ClientRequestBodyParameter::class.java).name(it.first)
        } else {
            it.first.name
        } to it.second
    }.toMap() + extraParameters

    return objectMapper.writeValueAsString(bodyParametersByName)
        .also { log.trace { """Method body parameters from "$methodArguments" completed.""" } }
}