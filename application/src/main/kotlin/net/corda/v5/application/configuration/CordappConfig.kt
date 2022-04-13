package net.corda.v5.application.configuration

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable

/**
 * Provides access to cordapp configuration independent of the configuration provider.
 */
@DoNotImplement
interface CordappConfig {
    /**
     * Check if a config exists at path
     */
    @Suspendable
    fun exists(path: String): Boolean

    /**
     * Get the value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    @Suspendable
    fun get(path: String): Any

    /**
     * Get the int value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    @Suspendable
    fun getInt(path: String): Int

    /**
     * Get the long value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    @Suspendable
    fun getLong(path: String): Long

    /**
     * Get the float value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    @Suspendable
    fun getFloat(path: String): Float

    /**
     * Get the double value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    @Suspendable
    fun getDouble(path: String): Double

    /**
     * Get the number value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    @Suspendable
    fun getNumber(path: String): Number

    /**
     * Get the string value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    @Suspendable
    fun getString(path: String): String

    /**
     * Get the boolean value of the configuration at "path".
     *
     * @throws CordappConfigException If the configuration fails to load, parse, or find a value.
     */
    @Suspendable
    fun getBoolean(path: String): Boolean
}