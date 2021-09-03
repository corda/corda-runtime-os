package net.corda.v5.httprpc.tools.annotations.extensions

import net.corda.v5.httprpc.api.annotations.HttpRpcResource

fun HttpRpcResource.name(annotated: Class<*>) = this.name.takeIf { it.isNotBlank() } ?: getClassSimpleName(annotated)
fun HttpRpcResource.path(annotated: Class<*>) = (this.path.takeIf { it.isNotBlank() } ?: getClassSimpleName(annotated)).toLowerCase()

private fun getClassSimpleName(clazz: Class<*>) =
    clazz.simpleName.substring(clazz.simpleName.lastIndexOf("$") + 1).replace("[]", "")