package net.corda.crypto.config.impl

import com.typesafe.config.Config

class CryptoBusProcessorConfig(internal val config: Config) : Config by config {
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