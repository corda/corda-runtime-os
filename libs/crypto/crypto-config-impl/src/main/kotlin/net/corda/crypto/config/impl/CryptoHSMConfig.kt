package net.corda.crypto.config.impl

import com.typesafe.config.Config
import net.corda.libs.configuration.SmartConfig

class CryptoHSMConfig(private val config: SmartConfig) {

    class RetryConfig(private val config: SmartConfig) {
        val maxAttempts: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
            try {
                config.getInt(this::maxAttempts.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::maxAttempts.name}", e)
            }
        }

        val attemptTimeoutMills: Long by lazy(LazyThreadSafetyMode.PUBLICATION) {
            try {
                config.getLong(this::attemptTimeoutMills.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::attemptTimeoutMills.name}", e)
            }
        }
    }

    val categories: List<CategoryConfig> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getConfigList(this::categories.name).map { CategoryConfig(it) }
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::categories.name}", e)
        }
    }

    val masterKeyPolicy: MasterKeyPolicy by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getEnum(MasterKeyPolicy::class.java, this::masterKeyPolicy.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::masterKeyPolicy.name}", e)
        }
    }

    val masterKeyAlias: String? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if(config.hasPath(this::masterKeyAlias.name)) {
            config.getString(this::masterKeyAlias.name)
        } else {
            null
        }
    }

    val supportedSchemes: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getStringList(this::supportedSchemes.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::supportedSchemes.name}", e)
        }
    }

    val capacity: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getInt(this::capacity.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::capacity.name}", e)
        }
    }

    val cfg: SmartConfig by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            config.getConfig(this::cfg.name)
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::cfg.name}", e)
        }
    }

    class CategoryConfig(private val config: Config) {
        val category: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
            try {
                config.getString(this::category.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::category.name}", e)
            }
        }

        val policy: PrivateKeyPolicy by lazy(LazyThreadSafetyMode.PUBLICATION) {
            try {
                config.getEnum(PrivateKeyPolicy::class.java, this::policy.name)
            } catch (e: Throwable) {
                throw IllegalStateException("Failed to get ${this::policy.name}", e)
            }
        }
    }

    val retry: RetryConfig by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            RetryConfig(config.getConfig(this::retry.name))
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to get ${this::retry.name}", e)
        }
    }
}