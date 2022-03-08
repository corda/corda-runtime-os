package net.corda.membership.impl.registration.staticnetwork

import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.GroupPolicyExtension.Companion.GROUP_ID
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.KEY_ALIAS
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.MEMBER_STATUS
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.NAME
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.PROTOCOL_PARAMS
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_MEMBERS
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_MGM
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.STATIC_NETWORK_TEMPLATE
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.types.MemberX500Name
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

class TestUtils {
    companion object {
        const val MGM_KEY_ALIAS = "mgm-alias"
        const val DUMMY_GROUP_ID = "dummy_group"

        private const val TEST_ENDPOINT_PROTOCOL = "1"
        private const val TEST_ENDPOINT_URL = "https://dummyurl.corda5.r3.com:10000"

        private val bootConfig: SmartConfig = mock()
        private val messagingConfig: SmartConfig = mock {
            on(it.withFallback(any())).thenReturn(mock())
        }

        val configs = mapOf(
            ConfigKeys.BOOT_CONFIG to bootConfig,
            ConfigKeys.MESSAGING_CONFIG to messagingConfig
        )

        val aliceName = MemberX500Name("Alice", "London", "GB")
        val bobName = MemberX500Name("Bob", "London", "GB")
        val charlieName = MemberX500Name("Charlie", "London", "GB")
        val daisyName = MemberX500Name("Daisy", "London", "GB")
        val ericName = MemberX500Name("Eric", "London", "GB")
        val frankieName = MemberX500Name("Frankie", "London", "GB")

        private val dummyMap = mapOf("key" to "value")

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

        private val staticMgmTemplate = mapOf("keyAlias" to MGM_KEY_ALIAS)

        val groupPolicyWithStaticNetwork = GroupPolicyImpl(
            mapOf(
                GROUP_ID to DUMMY_GROUP_ID,
                PROTOCOL_PARAMS to mapOf(
                    STATIC_NETWORK_TEMPLATE to mapOf(
                        STATIC_MGM to staticMgmTemplate,
                        STATIC_MEMBERS to staticMemberTemplate
                    )
                )
            )
        )

        val groupPolicyWithCastingFailure = GroupPolicyImpl(
            mapOf(
                PROTOCOL_PARAMS to mapOf(
                    STATIC_NETWORK_TEMPLATE to mapOf(
                        STATIC_MEMBERS to dummyMap
                    )
                )
            )
        )

        val groupPolicyWithInvalidStaticNetworkTemplate = GroupPolicyImpl(
            mapOf(
                PROTOCOL_PARAMS to mapOf(
                    STATIC_NETWORK_TEMPLATE to mapOf(
                        STATIC_MEMBERS to listOf(dummyMap)
                    )
                )
            )
        )

        val groupPolicyWithoutStaticNetwork = GroupPolicyImpl(emptyMap())

        val groupPolicyWithDuplicateMembers = GroupPolicyImpl(
            mapOf(
                GROUP_ID to DUMMY_GROUP_ID,
                PROTOCOL_PARAMS to mapOf(
                    STATIC_NETWORK_TEMPLATE to mapOf(
                        STATIC_MGM to staticMgmTemplate,
                        STATIC_MEMBERS to staticMemberTemplateWithDuplicateMembers
                    )
                )
            )
        )

        val groupPolicyWithoutMgm = GroupPolicyImpl(
            mapOf(
                GROUP_ID to DUMMY_GROUP_ID,
                PROTOCOL_PARAMS to mapOf(
                    STATIC_NETWORK_TEMPLATE to mapOf(
                        STATIC_MEMBERS to staticMemberTemplate
                    )
                )
            )
        )

        val groupPolicyWithoutMgmKeyAlias = GroupPolicyImpl(
            mapOf(
                GROUP_ID to DUMMY_GROUP_ID,
                PROTOCOL_PARAMS to mapOf(
                    STATIC_NETWORK_TEMPLATE to mapOf(
                        STATIC_MGM to dummyMap,
                        STATIC_MEMBERS to staticMemberTemplate
                    )
                )
            )
        )
    }
}