package net.corda.membership

/**
 * Factory class for creating [GroupPolicy] objects.
 */
interface GroupPolicyFactory {
    /**
     * Function for creating a [GroupPolicy] object from a JSON string. This JSON string should be the contents of the
     * GroupPolicy.json file which is distributed within a CPI.
     *
     * @param groupPolicyJson String representation of the GroupPolicy.json file.
     */
    fun createGroupPolicy(groupPolicyJson: String): GroupPolicy
}