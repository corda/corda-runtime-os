package net.corda.httprpc.client.processing

import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.tools.annotations.extensions.name
import net.corda.httprpc.tools.annotations.validation.utils.asPathParam
import java.lang.reflect.Method

internal fun Method.pathParametersFrom(allParameters: Array<out Any?>): Map<String, String> =
    this.parameters
        .mapIndexed { index, parameter -> parameter to allParameters[index] }
        .filter { it.first.annotations.any { annotation -> annotation is HttpRpcPathParameter } }
        .associate { it.first.getAnnotation(HttpRpcPathParameter::class.java).name(it.first) to it.second.toString().encodeParam() }

internal fun String.replacePathParameters(pathParams: Map<String, String>): String {
    var replacedString = this
    pathParams.forEach {
        replacedString = replacedString.replace(it.key.asPathParam, it.value)
    }
    return replacedString
}
