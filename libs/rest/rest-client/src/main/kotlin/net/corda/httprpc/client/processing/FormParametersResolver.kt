package net.corda.httprpc.client.processing

import net.corda.httprpc.annotations.RestRequestBodyParameter
import net.corda.httprpc.tools.annotations.extensions.name
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.Parameter

private val log = LoggerFactory.getLogger("net.corda.rest.client.internal.processing.FormParametersResolver.kt")

internal fun Method.formParametersFrom(methodArguments: Array<out Any?>): Map<String, String> {
    log.trace { """Method form parameters from "$methodArguments".""" }

    val formParameters = this.parameters
        .mapIndexed { index, parameter -> parameter to methodArguments[index] }
        .filter { it.first.type == String::class.java }

    val formParametersByName: Map<String, String> = formParameters.associate {
        getFieldNameFromAnnotationOrParameter(it) to it.second as String
    }

    return formParametersByName
        .also { if(formParametersByName.isNotEmpty()) log.trace { """Form parameters from "$methodArguments" completed.""" } }
}

private fun getFieldNameFromAnnotationOrParameter(it: Pair<Parameter, Any?>): String {
    val name = if (it.first.annotations.any { annotation -> annotation is RestRequestBodyParameter }) {
        it.first.getAnnotation(RestRequestBodyParameter::class.java).name(it.first)
    } else {
        it.first.name
    }
    return name
}
