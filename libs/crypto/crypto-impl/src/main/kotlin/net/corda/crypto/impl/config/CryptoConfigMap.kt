package net.corda.crypto.impl.config

import net.corda.v5.crypto.exceptions.CryptoConfigurationException

open class CryptoConfigMap(
    map: Map<String, Any?>
) : Map<String, Any?> by map {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun getOptionalConfig(raw: Map<String, Any?>, key: String): CryptoConfigMap? {
            val map = raw[key] as? Map<String, Any?>
                ?: return null
            return CryptoConfigMap(map)
        }

        @Suppress("UNCHECKED_CAST")
        fun getConfig(raw: Map<String, Any?>, key: String): CryptoConfigMap {
            val map = raw[key] as? Map<String, Any?>
                ?: throw CryptoConfigurationException("The key '$key' is not defined.")
            return CryptoConfigMap(map)
        }

        fun getLong(raw: Map<String, Any?>, key: String): Long {
            val value = raw[key]
                ?: throw CryptoConfigurationException("The key '$key' is not defined.")
            return when (value) {
                is Number -> value.toLong()
                is String -> value.toLong()
                else -> throw CryptoConfigurationException("Cannot convert value with the key '$key' to Long.")
            }
        }

        fun getLong(raw: Map<String, Any?>, key: String, default: Long): Long {
            val value = raw[key]
                ?: return default
            return when (value) {
                is Number -> value.toLong()
                is String -> value.toLong()
                else -> throw CryptoConfigurationException("Cannot convert value with the key '$key' to Long.")
            }
        }

        fun getString(raw: Map<String, Any?>, key: String): String {
            val value = raw[key]
                ?: throw CryptoConfigurationException("The key '$key' is not defined.")
            return if (value is String) {
                value
            } else {
                value.toString()
            }
        }

        fun getString(raw: Map<String, Any?>, key: String, default: String): String {
            val value = raw[key]
                ?: return default
            return if (value is String) {
                value
            } else {
                value.toString()
            }
        }

        fun getBoolean(raw: Map<String, Any?>, key: String): Boolean {
            val value = raw[key]
                ?: throw CryptoConfigurationException("The key '$key' is not defined.")
            return when (value) {
                is Boolean -> value
                is Number -> value.toLong() != 0L
                is String -> if (value.trim() == "1") {
                    true
                } else {
                    value.toBoolean()
                }
                else -> throw CryptoConfigurationException("Cannot convert value with the key '$key' to Boolean.")
            }
        }

        fun getBoolean(raw: Map<String, Any?>, key: String, default: Boolean): Boolean {
            val value = raw[key]
                ?: return default
            return when (value) {
                is Boolean -> value
                is Number -> value.toLong() != 0L
                is String -> if (value.trim() == "1") {
                    true
                } else {
                    value.toBoolean()
                }
                else -> throw CryptoConfigurationException("Cannot convert value with the key '$key' to Boolean.")
            }
        }
    }

    fun getOptionalConfig(key: String): CryptoConfigMap? = getOptionalConfig(this, key)

    fun getConfig(key: String): CryptoConfigMap = getConfig(this, key)

    fun getLong(key: String): Long = getLong(this, key)

    fun getLong(key: String, default: Long): Long = getLong(this, key, default)

    fun getString(key: String): String = getString(this, key)

    fun getString(key: String, default: String): String = getString(this, key, default)

    fun getBoolean(key: String): Boolean = getBoolean(this, key)

    fun getBoolean(key: String, default: Boolean): Boolean = getBoolean(this, key, default)
}