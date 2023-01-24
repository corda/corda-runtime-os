package net.corda.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.HttpRpcQueryParameter
import java.lang.reflect.Parameter

fun HttpRpcQueryParameter.name(annotated: Parameter) = this.name.takeIf { it.isNotBlank() } ?: annotated.name.lowercase()
fun HttpRpcQueryParameter.name(name: String) = this.name.takeIf { it.isNotBlank() } ?: name.lowercase()
