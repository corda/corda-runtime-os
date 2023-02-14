package net.corda.v5.membership;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.types.LayeredPropertyMap;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collection;

/**
 * This interface represents a set of group parameters under which all members of a group are expected to abide by.
 * Parameters are stored as a [LayeredPropertyMap] and exposed via interface properties.
 *
 * Note: any values in the group parameters values map need to be
 * a.) serializable for P2P (AMQP) and checkpoints (Kryo)
 * b.) comparable with .equals()
 *
 * Example usages:
 *
 * ```java
 * GroupParameters groupParameters = fullTransaction.getMembershipParameters();
 * int minimumPlatformVersion = groupParameters.getMinimumPlatformVersion();
 * Instant modifiedTime = groupParameters.getModifiedTime();
 * int epoch = groupParameters.getEpoch();
 * Collection<NotaryInfo> notaries = groupParameters.getNotaries();
 * ```
 *
 * ```kotlin
 * val groupParameters = fullTransaction.membershipParameters
 * val minimumPlatformVersion = groupParameters?.minimumPlatformVersion
 * val modifiedTime = groupParameters?.modifiedTime
 * val epoch = groupParameters?.epoch
 * val notaries = groupParameters?.notaries
 * ```
 */
@CordaSerializable
public interface GroupParameters extends LayeredPropertyMap {

    /**
     * @return The minimum platform version required to be running on in order to transact within a group.
     */
    int getMinimumPlatformVersion();

    /**
     * @return The [Instant] representing the last time the group parameters were modified.
     */
    @NotNull Instant getModifiedTime();

    /**
     * @return An [Int] representing the version of the group parameters. This is incremented on each modification to
     * the group parameters.
     */
    int getEpoch();

    /**
     * @return A collection of all available notary services in the group.
     */
    @NotNull Collection<NotaryInfo> getNotaries();
}
