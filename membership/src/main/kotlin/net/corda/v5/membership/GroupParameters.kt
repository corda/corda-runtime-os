package net.corda.v5.membership

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.LayeredPropertyMap
import java.time.Instant

/**
 * This interface represents a set of group parameters under which all members of a group are expected to abide by.
 * Parameters are stored as a [LayeredPropertyMap] and exposed via interface properties.
 *
 * Note: any values in the group parameters values map need to be
 * a.) serializable for P2P (AMQP) and checkpoints (Kryo)
 * b.) comparable with .equals()
 */
@CordaSerializable
interface GroupParameters : LayeredPropertyMap {
    /**
     * The minimum platform version required to be running on in order to transact within a group.
     */
    val minimumPlatformVersion: Int

    /**
     * The [Instant] representing the last time the group parameters were modified.
     */
    val modifiedTime: Instant

    /**
     * An [Int] representing the version of the group parameters. This is incremented on each modification to the
     * group parameters.
     */
    val epoch: Int
}