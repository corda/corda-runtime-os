package net.corda.rest.tools.annotations.extensions

import net.corda.rest.annotations.ClientRequestBodyParameter
import java.lang.reflect.Parameter

fun ClientRequestBodyParameter.name(annotated: Parameter) = this.name.takeIf { it.isNotBlank() } ?: annotated.name
fun ClientRequestBodyParameter.name(name: String) = this.name.takeIf { it.isNotBlank() } ?: name