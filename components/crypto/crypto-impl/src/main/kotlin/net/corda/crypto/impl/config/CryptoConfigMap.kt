package net.corda.crypto.impl.config

import net.corda.v5.crypto.exceptions.CryptoConfigurationException

open class CryptoConfigMap(
    protected val map: Map<String, Any?>
) : Map<String, Any?> by map {

    @Suppress("UNCHECKED_CAST")
    fun getOptionalConfig(key: String): CryptoConfigMap? {
        val map = get(key) as? Map<String, Any?>
            ?: return null
        return CryptoConfigMap(map)
    }

    @Suppress("UNCHECKED_CAST")
    fun getConfig(key: String): CryptoConfigMap {
        val map = get(key) as? Map<String, Any?>
            ?: throw CryptoConfigurationException("The key '$key' is not defined.")
        return CryptoConfigMap(map)
    }

    fun getLong(key: String): Long {
        val value = get(key)
            ?: throw CryptoConfigurationException("The key '$key' is not defined.")
        return when(value) {
            is Number -> value.toLong()
            is String -> value.toLong()
            else -> throw CryptoConfigurationException("Cannot convert value with the key '$key' to Long.")
        }
    }

    fun getLong(key: String, default: Long): Long {
        val value = get(key)
            ?: return default
        return when(value) {
            is Number -> value.toLong()
            is String -> value.toLong()
            else -> throw CryptoConfigurationException("Cannot convert value with the key '$key' to Long.")
        }
    }

    fun getString(key: String): String {
        val value = get(key)
            ?: throw CryptoConfigurationException("The key '$key' is not defined.")
        return if(value is String) {
            value
        } else {
            value.toString()
        }
    }

    fun getString(key: String, default: String): String {
        val value = get(key)
            ?: return default
        return if(value is String) {
            value
        } else {
            value.toString()
        }
    }
}