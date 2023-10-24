package net.corda.interop.group.policy.read

import java.util.*
import net.corda.lifecycle.Lifecycle

/**
 * Service for retrieving an interop group policy for a given group id.
 */
interface InteropGroupPolicyReadService: Lifecycle {
    /**
     * Retrieves the [String] object for a given groupId.
     **
     * @param [groupId] The group id for the lookup.
     * @return The current [String] group policy json content for the given interop group id.
     *  Returns null if no group policy was found or if error occurs when retrieving group policy.
     */
    fun getGroupPolicy(groupId: UUID): String?
}
