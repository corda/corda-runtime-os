package net.corda.membership.lib.impl.utils

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.EndpointInfoFactory
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.impl.MGMContextImpl
import net.corda.membership.lib.impl.MemberContextImpl
import net.corda.membership.lib.impl.MemberInfoImpl
import net.corda.test.util.time.TestClock
import net.corda.v5.membership.MemberInfo
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.time.Instant
import java.util.UUID

private const val KEY = "12345"
private val key: PublicKey = Mockito.mock(PublicKey::class.java)
internal val keyEncodingService: CipherSchemeMetadata = mock {
    on { decodePublicKey(KEY) } doReturn key
    on { encodeAsString(key) } doReturn KEY
}

private val clock = TestClock(Instant.ofEpochSecond(100))
private val modifiedTime = clock.instant()
private val endpointInfoFactory: EndpointInfoFactory = mock {
    on { create(any(), any()) } doAnswer { invocation ->
        mock {
            on { this.url } doReturn invocation.getArgument(0)
            on { this.protocolVersion } doReturn invocation.getArgument(1)
        }
    }
}
private val endpoints = listOf(
    endpointInfoFactory.create("https://localhost:10000"), endpointInfoFactory.create("https://google.com", 10)
)
internal val ledgerKeys = listOf(key, key)

private const val NULL_KEY = "nullKey"
private const val DUMMY_KEY = "dummyKey"

@Suppress("SpreadOperator")
fun createDummyMemberInfo(
    converters: List<CustomPropertyConverter<out Any>>,
    additionalMemberContext: List<Pair<String, String>> = emptyList(),
): MemberInfo = MemberInfoImpl(
    memberProvidedContext = createDummyMemberContext(converters, additionalMemberContext),
    mgmProvidedContext = createDummyMgmContext(converters)
)

@Suppress("SpreadOperator")
fun createDummyMemberContext(
    converters: List<CustomPropertyConverter<out Any>>,
    additionalMemberContext: List<Pair<String, String>> = emptyList(),
) = LayeredPropertyMapMocks.create<MemberContextImpl>(
    sortedMapOf(
        MemberInfoExtension.PARTY_NAME to "O=Alice,L=London,C=GB",
        String.format(MemberInfoExtension.PARTY_SESSION_KEYS, 0) to KEY,
        MemberInfoExtension.GROUP_ID to UUID(0,1).toString(),
        *convertPublicKeys().toTypedArray(),
        *convertEndpoints().toTypedArray(),
        MemberInfoExtension.SOFTWARE_VERSION to "5.0.0",
        MemberInfoExtension.PLATFORM_VERSION to "5000",
        DUMMY_KEY to "dummyValue",
        NULL_KEY to null,
        *additionalMemberContext.toTypedArray(),
    ), converters
)

fun createDummyMgmContext(
    converters: List<CustomPropertyConverter<out Any>>,
) = LayeredPropertyMapMocks.create<MGMContextImpl>(
    sortedMapOf(
        MemberInfoExtension.STATUS to MemberInfoExtension.MEMBER_STATUS_ACTIVE,
        MemberInfoExtension.MODIFIED_TIME to modifiedTime.toString(),
        DUMMY_KEY to "dummyValue",
        MemberInfoExtension.SERIAL to "1",
    ), converters
)

fun convertEndpoints(): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    for (i in endpoints.indices) {
        result.add(Pair(String.format(MemberInfoExtension.URL_KEY, i), endpoints[i].url))
        result.add(
            Pair(
                String.format(MemberInfoExtension.PROTOCOL_VERSION, i),
                endpoints[i].protocolVersion.toString()
            )
        )
    }
    return result
}

fun convertPublicKeys(): List<Pair<String, String>> = ledgerKeys.mapIndexed { i, ledgerKey ->
    String.format(
        MemberInfoExtension.LEDGER_KEYS_KEY, i
    ) to keyEncodingService.encodeAsString(ledgerKey)
}
