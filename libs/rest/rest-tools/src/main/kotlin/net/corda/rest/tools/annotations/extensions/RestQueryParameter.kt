package net.corda.rest.tools.annotations.extensions

import net.corda.rest.annotations.RestQueryParameter
import java.lang.reflect.Parameter

fun RestQueryParameter.name(annotated: Parameter) = this.name.takeIf { it.isNotBlank() } ?: annotated.name.lowercase()
fun RestQueryParameter.name(name: String) = this.name.takeIf { it.isNotBlank() } ?: name.lowercase()
