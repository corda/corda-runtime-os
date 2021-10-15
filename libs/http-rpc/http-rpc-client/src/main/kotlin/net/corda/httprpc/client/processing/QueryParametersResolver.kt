package net.corda.httprpc.client.processing

import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.tools.annotations.extensions.name
import net.corda.v5.base.util.uncheckedCast
import java.lang.reflect.Method

internal fun Method.queryParametersFrom(allParameters: Array<out Any?>): Map<String, Any?> =
    this.parameters
        .mapIndexed { index, parameter -> parameter to allParameters[index] }
        .filter { it.first.annotations.any { annotation -> annotation is HttpRpcQueryParameter } }
        .associate {
            it.first.getAnnotation(HttpRpcQueryParameter::class.java).name(it.first) to it.second.encodeQueryParam()
        }

private fun Any?.encodeQueryParam(): Any? {
    return if (this == null) {
        null
    } else if (Iterable::class.java.isAssignableFrom(this::class.java)) {
        val list: Iterable<Any?> = uncheckedCast(this)
        list.map { it?.toString()?.encodeParam() }
    } else {
        toString().encodeParam()
    }
}