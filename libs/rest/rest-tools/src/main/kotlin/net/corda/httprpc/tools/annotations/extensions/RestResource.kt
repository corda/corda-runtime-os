package net.corda.rest.tools.annotations.extensions

import net.corda.rest.annotations.HttpRestResource

fun HttpRestResource.name(annotated: Class<*>) = this.name.takeIf { it.isNotBlank() } ?: getClassSimpleName(annotated)
fun HttpRestResource.path(annotated: Class<*>) = (this.path.takeIf { it.isNotBlank() } ?: getClassSimpleName(annotated)).lowercase()

private fun getClassSimpleName(clazz: Class<*>) =
    clazz.simpleName.substring(clazz.simpleName.lastIndexOf("$") + 1).replace("[]", "")
