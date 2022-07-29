package net.corda.crypto.config.impl

import net.corda.libs.configuration.SmartConfig

class CryptoBusProcessorConfig(internal val config: SmartConfig) : SmartConfig by config {
    val maxAttempts: Int
        get() = try {
            config.getInt(this::maxAttempts.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::maxAttempts.name}", e)
        }

    val waitBetweenMills: List<Long>
        get() = try {
            config.getLongList(this::waitBetweenMills.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::waitBetweenMills.name}", e)
        }
}