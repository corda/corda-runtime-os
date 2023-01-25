package net.corda.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.RestPathParameter
import java.lang.reflect.Parameter

fun RestPathParameter.name(annotated: Parameter) = this.name.takeIf { it.isNotBlank() } ?: annotated.name.lowercase()
fun RestPathParameter.name(name: String) = this.name.takeIf { it.isNotBlank() } ?: name.lowercase()
