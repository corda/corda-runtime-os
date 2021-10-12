package net.corda.membership.impl

import net.corda.data.identity.WireMemberInfo
import net.corda.membership.impl.MemberInfoExtension.Companion.CERTIFICATE
import net.corda.membership.impl.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.impl.MemberInfoExtension.Companion.IDENTITY_KEYS_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.impl.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.impl.MemberInfoExtension.Companion.NOTARY_SERVICE_PARTY_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.NOTARY_SERVICE_PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.impl.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.STATUS
import net.corda.membership.impl.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.endpoints
import net.corda.membership.impl.MemberInfoExtension.Companion.modifiedTime
import net.corda.v5.application.identity.Party
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.identity.KeyValueStore
import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.identity.StringObjectConverter
import net.corda.v5.membership.identity.parse
import net.corda.v5.membership.identity.parseList
import net.corda.v5.membership.identity.toWireKeyValuePairList
import net.corda.v5.membership.identity.ValueNotFoundException
import org.apache.avro.file.DataFileReader
import org.apache.avro.file.DataFileWriter
import org.apache.avro.io.DatumReader
import org.apache.avro.io.DatumWriter
import org.apache.avro.specific.SpecificDatumReader
import org.apache.avro.specific.SpecificDatumWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.io.File
import java.lang.NullPointerException
import java.security.PublicKey
import java.time.Instant
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MemberInfoTest {
    companion object {
        private val keyEncodingService = Mockito.mock(KeyEncodingService::class.java)
        private const val KEY = "12345"
        private val key = Mockito.mock(PublicKey::class.java)

        private val modifiedTime = Instant.now()
        private val endpoints = listOf(EndpointInfoImpl("https://localhost:10000", 1), EndpointInfoImpl("https://google.com", 10))
        private val identityKeys = listOf(key, key)
        private val testObjects = listOf(TestObject(1, "dummytext1"), TestObject(2, "dummytext2"))

        private const val TEST_OBJECT_NUMBER = "custom.testObjects.%s.number"
        private const val TEST_OBJECT_TEXT = "custom.testObjects.%s.text"

        private fun createDummyMemberInfo(): MemberInfo = MemberInfoImpl(
            memberProvidedContext = KeyValueStoreImpl(
                sortedMapOf(
                    PARTY_NAME to "O=Alice,L=London,C=GB",
                    PARTY_OWNING_KEY to KEY,
                    NOTARY_SERVICE_PARTY_NAME to "O=Notary,L=London,C=GB",
                    NOTARY_SERVICE_PARTY_KEY to KEY,
                    GROUP_ID to "DEFAULT_MEMBER_GROUP_ID",
                    *convertPublicKeys(keyEncodingService).toTypedArray(),
                    *convertEndpoints().toTypedArray(),
                    *convertTestObjects().toTypedArray(),
                    SOFTWARE_VERSION to "5.0.0",
                    PLATFORM_VERSION to "10",
                    SERIAL to "1",
                    CERTIFICATE to "dummy_cert_path"
                ),
                keyEncodingService
            ),
            mgmProvidedContext = KeyValueStoreImpl(
                sortedMapOf(
                    STATUS to MEMBER_STATUS_ACTIVE,
                    MODIFIED_TIME to modifiedTime.toString()
                ),
                keyEncodingService
            )
        )

        private fun convertEndpoints(): List<Pair<String, String>> {
            val result = mutableListOf<Pair<String, String>>()
            for(i in endpoints.indices) {
                result.add(Pair(String.format(URL_KEY, i), endpoints[i].url))
                result.add(Pair(String.format(PROTOCOL_VERSION, i), endpoints[i].protocolVersion.toString()))
            }
            return result
        }

        private fun convertPublicKeys(keyEncodingService: KeyEncodingService): List<Pair<String, String>> {
            val result = mutableListOf<Pair<String, String>>()
            for(i in identityKeys.indices) {
                result.add(Pair(String.format(IDENTITY_KEYS_KEY, i), keyEncodingService.encodeAsString(identityKeys[i])))
            }
            return result
        }

        private fun convertTestObjects(): List<Pair<String, String>> {
            val result = mutableListOf<Pair<String, String>>()
            for(i in testObjects.indices) {
                result.add((Pair(String.format(TEST_OBJECT_NUMBER, i), testObjects[i].number.toString())))
                result.add((Pair(String.format(TEST_OBJECT_TEXT, i), testObjects[i].text)))
            }
            return result
        }

        val MemberInfo.testObjects: List<TestObject>
            get() = this.memberProvidedContext.parseList("custom.testObjects", TestStringConverter())

        private var memberInfo: MemberInfo? = null
    }

    @BeforeEach
    fun setUp() {
        whenever(
            keyEncodingService.decodePublicKey(KEY)
        ).thenReturn(key)
        whenever(
            keyEncodingService.encodeAsString(key)
        ).thenReturn(KEY)

        memberInfo = createDummyMemberInfo()
    }

    @Test
    fun `serializing and deserializing WireMemberInfo using avro`() {
        var user: WireMemberInfo? = null
        val userDatumWriter: DatumWriter<WireMemberInfo> = SpecificDatumWriter(
            WireMemberInfo::class.java
        )
        val dataFileWriter: DataFileWriter<WireMemberInfo> = DataFileWriter(userDatumWriter)
        memberInfo = createDummyMemberInfo()
        val wireMemberInfo = WireMemberInfo(
            memberInfo?.memberProvidedContext?.toWireKeyValuePairList(),
            memberInfo?.mgmProvidedContext?.toWireKeyValuePairList()
        )

        dataFileWriter.create(wireMemberInfo.schema, File("avro-member-info.avro"))
        dataFileWriter.append(wireMemberInfo)
        dataFileWriter.close()

        val userDatumReader: DatumReader<WireMemberInfo> = SpecificDatumReader(
            WireMemberInfo::class.java
        )
        val dataFileReader: DataFileReader<WireMemberInfo> =
            DataFileReader(File("avro-member-info.avro"), userDatumReader)

        var recreatedMemberInfo: MemberInfo? = null
        while (dataFileReader.hasNext()) {
            user = dataFileReader.next(user)
            recreatedMemberInfo = toMemberInfo(
                user.memberContext.toKeyValueStore(keyEncodingService),
                user.mgmContext.toKeyValueStore(keyEncodingService)
            )
        }

        assertEquals(memberInfo, recreatedMemberInfo)
        assertEquals(memberInfo?.identityKeys, recreatedMemberInfo?.identityKeys)
        assertEquals(memberInfo?.name, recreatedMemberInfo?.name)
        assertEquals(memberInfo?.owningKey, recreatedMemberInfo?.owningKey)
        assertEquals(memberInfo?.endpoints, recreatedMemberInfo?.endpoints)
        assertEquals(memberInfo?.modifiedTime, recreatedMemberInfo?.modifiedTime)
        assertEquals(memberInfo?.isActive, recreatedMemberInfo?.isActive)
        assertEquals(memberInfo?.serial, recreatedMemberInfo?.serial)
        assertEquals(memberInfo?.platformVersion, recreatedMemberInfo?.platformVersion)
        assertEquals(memberInfo?.party, recreatedMemberInfo?.party)
    }

    @Test
    fun `extension function and parser to retrieve list of custom objects works`() {
        assertEquals(testObjects, memberInfo?.testObjects)
    }

    @Test
    fun `parser fails with ValueNotFoundException when there is no value for a key`() {
        assertFailsWith<ValueNotFoundException> {
            memberInfo?.mgmProvidedContext?.parse("dummyKey")
        }
    }

    @Test
    fun `list parser returns back emptyList when no value is found`() {
        assertTrue {
            memberInfo?.memberProvidedContext?.parseList<String>("dummyKey")?.isEmpty()!!
        }
    }

    val PARTY = "corda.party"

    val MemberInfo.party: Party
        get() = memberProvidedContext.parse(PARTY)

    val NOTARY_SERVICE_PARTY = "corda.notaryServiceParty"

    val MemberInfo.notaryServiceParty: Party?
        get() = memberProvidedContext.parse(NOTARY_SERVICE_PARTY)
}

data class TestObject(val number: Int, val text: String)

class TestStringConverter : StringObjectConverter<TestObject> {
    override fun convert(stringProperties: KeyValueStore, clazz: Class<out TestObject>): TestObject {
        return TestObject(
            stringProperties["number"]?.toInt()
                ?: throw NullPointerException(),
            stringProperties["text"]
                ?: throw NullPointerException()
        )
    }
}