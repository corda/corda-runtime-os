package net.corda.v5.httprpc.tools.annotations.extensions

import net.corda.httprpc.annotations.HttpRpcQueryParameter
import java.lang.reflect.Parameter

fun HttpRpcQueryParameter.name(annotated: Parameter) = this.name.takeIf { it.isNotBlank() } ?: annotated.name.toLowerCase()
fun HttpRpcQueryParameter.name(name: String) = this.name.takeIf { it.isNotBlank() } ?: name.toLowerCase()
