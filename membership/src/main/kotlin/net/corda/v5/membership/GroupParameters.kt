@file:JvmName("GroupParametersUtils")
package net.corda.v5.membership

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.time.Instant

/**
 * Note: any values in the groupParametersValues map need to be
 * a.) serializable for P2P (AMQP) and checkpoints (Kryo)
 * b.) comparable with .equals()
 */
@CordaSerializable
interface GroupParameters {

    /**
     * Gets the group parameter property matching the input [key].
     *
     * @param key The key.
     *
     * @returns The matching group parameter property.
     */
    operator fun get(key: String): Any?

    /**
     * Gets the keys of each group parameter key-pair.
     */
    val keys: Set<String>

    companion object {

        const val MINIMUM_PLATFORM_VERSION_KEY = "corda.minimumPlatformVersion"
        const val MODIFIED_TIME_KEY = "corda.modifiedTime"
        const val EPOCH_KEY = "corda.epoch"

        @JvmStatic
        val GroupParameters.minimumPlatformVersion: Int
            get() = getValue(MINIMUM_PLATFORM_VERSION_KEY)

        @JvmStatic
        val GroupParameters.modifiedTime: Instant
            get() = getValue(MODIFIED_TIME_KEY)

        @JvmStatic
        val GroupParameters.epoch: Int
            get() = getValue(EPOCH_KEY)
    }
}

inline fun <reified T> GroupParameters.getValue(key: String): T {
    return this[key] as T
}

/**
 * When a Corda feature cannot be used due to the node's compatibility zone not enforcing a high enough minimum platform
 * version.
 */
class ZoneVersionTooLowException(message: String) : CordaRuntimeException(message)
