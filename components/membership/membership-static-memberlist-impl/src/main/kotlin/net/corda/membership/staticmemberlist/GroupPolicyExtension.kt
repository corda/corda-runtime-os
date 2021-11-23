package net.corda.membership.staticmemberlist

import net.corda.membership.GroupPolicy

class GroupPolicyExtension {
    companion object {
        /** Key name for static member template property. */
        const val STATIC_MEMBER_TEMPLATE = "staticMemberTemplate"

        /** Key name for group ID property. */
        const val GROUP_ID = "groupId"

        /** Static member template. */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        val GroupPolicy.staticMemberTemplate: List<Map<String, String>>
            get() = this[STATIC_MEMBER_TEMPLATE] as List<Map<String, String>>
    }
}
