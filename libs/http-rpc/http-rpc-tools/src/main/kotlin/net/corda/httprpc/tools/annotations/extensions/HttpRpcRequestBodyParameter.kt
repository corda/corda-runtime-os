package net.corda.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.RestRequestBodyParameter
import java.lang.reflect.Parameter

fun RestRequestBodyParameter.name(annotated: Parameter) = this.name.takeIf { it.isNotBlank() } ?: annotated.name
fun RestRequestBodyParameter.name(name: String) = this.name.takeIf { it.isNotBlank() } ?: name