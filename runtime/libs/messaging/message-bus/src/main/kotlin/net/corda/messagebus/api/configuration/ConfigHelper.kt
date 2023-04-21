package net.corda.messagebus.api.configuration

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl

fun SmartConfig.getStringOrNull(path: String) = if (hasPath(path)) getString(path) else null
fun SmartConfig.getStringOrDefault(path: String, default: String): String = if (hasPath(path)) getString(path) else default
fun SmartConfig.getConfigOrEmpty(path: String): SmartConfig = if (hasPath(path)) getConfig(path) else SmartConfigImpl.empty()
