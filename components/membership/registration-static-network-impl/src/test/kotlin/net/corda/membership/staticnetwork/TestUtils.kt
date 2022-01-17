package net.corda.membership.staticnetwork

import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.impl.GroupPolicyExtension.Companion.GROUP_ID
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.KEY_ALIAS
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.MEMBER_STATUS
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.NAME
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_MEMBERS
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_MGM
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_NETWORK_TEMPLATE
import net.corda.v5.membership.identity.MemberX500Name

class TestUtils {
    companion object {
        const val MGM_KEY_ALIAS = "mgm-alias"
        const val DUMMY_GROUP_ID = "dummy_group"

        private const val TEST_ENDPOINT_PROTOCOL = "1"
        private const val TEST_ENDPOINT_URL = "https://dummyurl.corda5.r3.com:10000"

        val aliceName = MemberX500Name("Alice", "London", "GB")
        val bobName = MemberX500Name("Bob", "London", "GB")
        val charlieName = MemberX500Name("Charlie", "London", "GB")
        val daisyName = MemberX500Name("Daisy", "London", "GB")

        private val staticMemberTemplate: List<Map<String, String>> =
            listOf(
                mapOf(
                    NAME to aliceName.toString(),
                    KEY_ALIAS to "alice-alias",
                    MEMBER_STATUS to MEMBER_STATUS_ACTIVE,
                    String.format(ENDPOINT_URL, 1) to TEST_ENDPOINT_URL,
                    String.format(ENDPOINT_PROTOCOL, 1) to TEST_ENDPOINT_PROTOCOL
                ),
                mapOf(
                    NAME to bobName.toString(),
                    MEMBER_STATUS to MEMBER_STATUS_ACTIVE,
                    String.format(ENDPOINT_URL, 1) to TEST_ENDPOINT_URL,
                    String.format(ENDPOINT_PROTOCOL, 1) to TEST_ENDPOINT_PROTOCOL
                ),
                mapOf(
                    NAME to charlieName.toString(),
                    MEMBER_STATUS to MEMBER_STATUS_SUSPENDED,
                    String.format(ENDPOINT_URL, 1) to TEST_ENDPOINT_URL,
                    String.format(ENDPOINT_PROTOCOL, 1) to TEST_ENDPOINT_PROTOCOL,
                    String.format(ENDPOINT_URL, 2) to TEST_ENDPOINT_URL,
                    String.format(ENDPOINT_PROTOCOL, 2) to TEST_ENDPOINT_PROTOCOL
                )
            )

        private val staticMemberTemplateWithDuplicateMembers: List<Map<String, String>> =
            listOf(
                mapOf(
                    NAME to daisyName.toString(),
                    String.format(ENDPOINT_URL, 1) to TEST_ENDPOINT_URL,
                    String.format(ENDPOINT_PROTOCOL, 1) to TEST_ENDPOINT_PROTOCOL
                ),
                mapOf(
                    NAME to daisyName.toString(),
                    String.format(ENDPOINT_URL, 1) to TEST_ENDPOINT_URL,
                    String.format(ENDPOINT_PROTOCOL, 1) to TEST_ENDPOINT_PROTOCOL
                )
            )

        val groupPolicyWithStaticNetwork = GroupPolicyImpl(
            mapOf(
                GROUP_ID to DUMMY_GROUP_ID,
                STATIC_NETWORK_TEMPLATE to mapOf(
                    STATIC_MGM to mapOf(
                        "keyAlias" to MGM_KEY_ALIAS
                    ),
                    STATIC_MEMBERS to staticMemberTemplate
                )
            )
        )

        val groupPolicyWithCastingFailure = GroupPolicyImpl(
            mapOf(
                STATIC_NETWORK_TEMPLATE to mapOf(
                    STATIC_MEMBERS to mapOf("key" to "value")
                )
            )
        )

        val groupPolicyWithInvalidStaticNetworkTemplate = GroupPolicyImpl(
            mapOf(
                STATIC_NETWORK_TEMPLATE to mapOf(
                    STATIC_MEMBERS to listOf(mapOf("key" to "value"))
                )
            )
        )

        val groupPolicyWithoutStaticNetwork = GroupPolicyImpl(emptyMap())

        val groupPolicyWithDuplicateMembers = GroupPolicyImpl(
            mapOf(
                GROUP_ID to DUMMY_GROUP_ID,
                STATIC_NETWORK_TEMPLATE to mapOf(
                    STATIC_MEMBERS to staticMemberTemplateWithDuplicateMembers
                )
            )
        )
    }
}