package net.corda.rest.client.processing

import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.tools.annotations.extensions.name
import java.lang.reflect.Method
import java.lang.reflect.Parameter

internal fun Method.queryParametersFrom(allParameters: Array<out Any?>): Map<String, Any?> {
    val queryParameters: List<Pair<Parameter, Any?>> = parameters
        .mapIndexed { index, parameter -> parameter to allParameters[index] }
        .filter { it.first.annotations.any { annotation -> annotation is RestQueryParameter } }
    // Do not include into the map if it is `null` and not required
    val requiredQueryParameters = queryParameters
        .filterNot { it.second == null && !it.first.getAnnotation(RestQueryParameter::class.java).required }
    return requiredQueryParameters
        .associate {
            it.first.getAnnotation(RestQueryParameter::class.java).name(it.first) to it.second.encodeQueryParam()
        }
}

private fun Any?.encodeQueryParam(): Any? {
    return if (this == null) {
        null
    } else if (Iterable::class.java.isAssignableFrom(this::class.java)) {
        val list: Iterable<Any?> = this as Iterable<Any?>
        list.map { it?.toString()?.encodeParam() }
    } else {
        toString().encodeParam()
    }
}
