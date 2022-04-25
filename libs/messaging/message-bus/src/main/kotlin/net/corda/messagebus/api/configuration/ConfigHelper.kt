package net.corda.messagebus.api.configuration

import net.corda.libs.configuration.SmartConfig

fun SmartConfig.getStringOrNull(path: String) = if (hasPath(path)) getString(path) else null
fun SmartConfig.getStringOrDefault(path: String, default: String): String = if (hasPath(path)) getString(path) else default
