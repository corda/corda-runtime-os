package net.corda.membership.impl.registration.staticnetwork.cache

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.staticgroup.StaticGroupDefinition
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.notary.MemberNotaryKey
import net.corda.membership.lib.toMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.UUID

class GroupParametersCacheTest {
    private companion object {
        const val MPV = 5000
        const val EPOCH = 1
        const val KNOWN_NOTARY_SERVICE = "O=NotaryA, L=LDN, C=GB"
        const val KNOWN_NOTARY_PLUGIN = "net.corda.notary.MyNotaryService"
    }

    private val knownGroupId = UUID.randomUUID().toString()
    private val knownIdentity = HoldingIdentity(MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"), knownGroupId)
    private val platformInfoProvider: PlatformInfoProvider = mock {
        on { activePlatformVersion } doReturn MPV
    }
    private val keyEncodingService = mock<KeyEncodingService> {
        on { encodeAsString(any()) } doReturn "test-key"
    }

    @Test
    fun `group parameters are added to cache when set is called`() {
        val groupParameters = KeyValuePairList(mutableListOf(
            KeyValuePair(EPOCH_KEY, EPOCH.toString()),
            KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
            KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
            KeyValuePair("corda.notary.service.5.keys.0", "existing-test-key"),
        ))
        val groupParametersCache = GroupParametersCache(platformInfoProvider, keyEncodingService)

        groupParametersCache.set(knownGroupId, groupParameters)

        assertThat(groupParametersCache.getOrCreateGroupParameters(knownIdentity)).isEqualTo(groupParameters)
    }

    @Test
    fun `when cache is empty, getOrCreateGroupParameters publishes snapshot`() {
        val groupParametersCache = GroupParametersCache(platformInfoProvider, keyEncodingService)

        val (_, records) = groupParametersCache.getOrCreateGroupParameters(knownIdentity)

        with(records.first()) {
            assertThat(key).isEqualTo(knownGroupId)
            with(value as StaticGroupDefinition) {
                val params = this.groupParameters.toMap()
                assertThat(params[EPOCH_KEY]).isEqualTo("1")
                assertThat(params[MPV_KEY]).isEqualTo("5000")
                assertThat(Instant.parse(params[MODIFIED_TIME_KEY])).isBeforeOrEqualTo(Instant.now())
            }
        }
    }

    @Test
    fun `addNotary called with new notary service name adds new notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { servicePlugin } doReturn KNOWN_NOTARY_PLUGIN
        }
        val memberContext: MemberContext = mock {
            on { entries } doReturn mapOf("${ROLES_PREFIX}.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notary: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
            on { groupId } doReturn knownGroupId
        }
        val groupParametersCache = GroupParametersCache(platformInfoProvider, keyEncodingService)
        groupParametersCache.getOrCreateGroupParameters(knownIdentity)

        val (_, records) = groupParametersCache.addNotary(notary)!!

        with(records.first()) {
            assertThat(key).isEqualTo(knownGroupId)
            with(value as StaticGroupDefinition) {
                assertThat(this.groupParameters.items.containsAll(
                    listOf(
                        KeyValuePair(EPOCH_KEY, "2"),
                        KeyValuePair(MPV_KEY, MPV.toString()),
                        KeyValuePair("corda.notary.service.0.name", KNOWN_NOTARY_SERVICE),
                        KeyValuePair("corda.notary.service.0.plugin", KNOWN_NOTARY_PLUGIN),
                        KeyValuePair("corda.notary.service.0.keys.0", "test-key"),
                    )
                ))
            }
        }
    }

    @Test
    fun `addNotary called with new keys adds keys to existing notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { servicePlugin } doReturn KNOWN_NOTARY_PLUGIN
        }
        val memberContext: MemberContext = mock {
            on { entries } doReturn mapOf("${ROLES_PREFIX}.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notary: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
            on { groupId } doReturn knownGroupId
        }
        val groupParametersCache = GroupParametersCache(platformInfoProvider, keyEncodingService)
        val existingGroupParameters = KeyValuePairList(mutableListOf(
            KeyValuePair(EPOCH_KEY, EPOCH.toString()),
            KeyValuePair(MPV_KEY, MPV.toString()),
            KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
            KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
            KeyValuePair("corda.notary.service.5.keys.0", "existing-test-key"),
        ))
        groupParametersCache.set(knownGroupId, existingGroupParameters)

        val (_, records) = groupParametersCache.addNotary(notary)!!

        with(records.first()) {
            assertThat(key).isEqualTo(knownGroupId)
            with(value as StaticGroupDefinition) {
                assertThat(this.groupParameters.items.containsAll(
                    listOf(
                        KeyValuePair(EPOCH_KEY, "2"),
                        KeyValuePair(MPV_KEY, MPV.toString()),
                        KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                        KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
                        KeyValuePair("corda.notary.service.5.keys.0", "existing-test-key"),
                        KeyValuePair("corda.notary.service.5.keys.1", "test-key"),
                    )
                ))
            }
        }
    }
}