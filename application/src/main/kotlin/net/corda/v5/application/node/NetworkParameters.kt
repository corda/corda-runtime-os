package net.corda.v5.application.node

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.time.Instant

/**
 * Note: any values in the networkParametersValues map need to be
 * a.) serializable for P2P (AMQP) and checkpoints (Kryo)
 * b.) comparable with .equals()
 */
@CordaSerializable
interface NetworkParameters : CordaServiceInjectable, CordaFlowInjectable {

    /**
     * Gets the network parameter property matching the input [key].
     *
     * @param key The key.
     *
     * @returns The matching network parameter property.
     */
    operator fun get(key: String): Any?

    /**
     * Gets the keys of each network parameter key-pair.
     */
    val keys: Set<String>

    companion object {

        const val MINIMUM_PLATFORM_VERSION_KEY = "corda.minimumPlatformVersion"
        const val MODIFIED_TIME_KEY = "corda.modifiedTime"
        const val EPOCH_KEY = "corda.epoch"

        @JvmStatic
        val NetworkParameters.minimumPlatformVersion: Int
            get() = getValue(MINIMUM_PLATFORM_VERSION_KEY)

        @JvmStatic
        val NetworkParameters.modifiedTime: Instant
            get() = getValue(MODIFIED_TIME_KEY)

        @JvmStatic
        val NetworkParameters.epoch: Int
            get() = getValue(EPOCH_KEY)
    }
}

inline fun <reified T> NetworkParameters.getValue(key: String): T {
    return this[key] as T
}

/**
 * When a Corda feature cannot be used due to the node's compatibility zone not enforcing a high enough minimum platform
 * version.
 */
class ZoneVersionTooLowException(message: String) : CordaRuntimeException(message)
