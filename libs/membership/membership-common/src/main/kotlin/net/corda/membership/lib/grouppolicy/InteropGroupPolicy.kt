package net.corda.membership.lib.grouppolicy

import net.corda.membership.lib.exceptions.BadGroupPolicyException

interface InteropGroupPolicy {
    /**
     * Integer representing the version of the group policy file used for interop group policy.
     *
     * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
     */
    @get:Throws(BadGroupPolicyException::class)
    val fileFormatVersion: Int

    /**
     * Group Identifier.
     *
     * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
     */
    @get:Throws(BadGroupPolicyException::class)
    val groupId: String

    /**
     * Set of P2P configuration parameters for the group.
     *
     * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
     */
    @get:Throws(BadGroupPolicyException::class)
    val p2pParameters: GroupPolicy.P2PParameters

}