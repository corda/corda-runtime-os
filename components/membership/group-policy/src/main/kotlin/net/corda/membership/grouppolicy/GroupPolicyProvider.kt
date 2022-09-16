package net.corda.membership.grouppolicy

import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.virtualnode.HoldingIdentity

/**
 * Service for retrieving the group policy file for a given holding identity
 */
interface GroupPolicyProvider : Lifecycle {

    /**
     * Retrieves the [GroupPolicy] object for a given member based on the member's holding identity.
     **
     * @param holdingIdentity The holding identity of the member doing the lookup.
     * @return The current [GroupPolicy] file that was bundled with the CPI which was installed for the given holding
     *  identity. Returns null if no group policy was found or if error occurs when retrieving group policy.
     */
    fun getGroupPolicy(holdingIdentity: HoldingIdentity): GroupPolicy?

    /**
     * Registers a listener callback in order to be notified for creation of a new group policy or update of an existing one.
     *
     * @param callback A call back that will be called when a new group is created or an existing group changed.
     * @param name The listener name (the call back will be called only to one instance with that name).
     */
    fun registerListener(name: String, callback: (HoldingIdentity, GroupPolicy) -> Unit)
}