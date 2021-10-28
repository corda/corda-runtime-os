package net.corda.membership.identity

import net.corda.data.membership.WireMemberInfo
import net.corda.v5.membership.conversion.parse
import net.corda.v5.membership.conversion.parseList
import net.corda.v5.membership.conversion.parseOrNull
import net.corda.membership.conversion.PropertyConverterImpl
import net.corda.membership.conversion.toWireKeyValuePairList
import net.corda.membership.identity.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.identity.MemberInfoExtension.Companion.IDENTITY_KEYS
import net.corda.membership.identity.MemberInfoExtension.Companion.IDENTITY_KEYS_KEY
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.identity.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.identity.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.identity.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.identity.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.identity.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.identity.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.identity.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.identity.MemberInfoExtension.Companion.STATUS
import net.corda.membership.identity.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.identity.MemberInfoExtension.Companion.endpoints
import net.corda.membership.identity.MemberInfoExtension.Companion.groupId
import net.corda.membership.identity.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.identity.MemberInfoExtension.Companion.status
import net.corda.membership.identity.converter.EndpointInfoConverter
import net.corda.membership.identity.converter.PublicKeyConverter
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.conversion.ConversionContext
import net.corda.v5.membership.conversion.CustomPropertyConverter
import net.corda.v5.membership.conversion.ValueNotFoundException
import net.corda.v5.membership.identity.EndpointInfo
import net.corda.v5.membership.identity.MemberInfo
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
import org.osgi.service.component.annotations.Component
import java.io.File
import java.security.PublicKey
import java.time.Instant
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("MaxLineLength")
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

        private val converter = PropertyConverterImpl(
            listOf(
                EndpointInfoConverter(),
                PublicKeyConverter(keyEncodingService),
                TestStringConverter()
            )
        )

        private const val NULL_KEY = "nullKey"
        private const val DUMMY_KEY = "dummyKey"

        private const val INVALID_LIST_KEY = "invalidList"
        private val MemberInfo.dummy: List<String>
            get() = memberProvidedContext.parseList(INVALID_LIST_KEY)

        @Suppress("SpreadOperator")
        private fun createDummyMemberInfo(): MemberInfo = MemberInfoImpl(
            memberProvidedContext = MemberContextImpl(
                sortedMapOf(
                    PARTY_NAME to "O=Alice,L=London,C=GB",
                    PARTY_OWNING_KEY to KEY,
                    GROUP_ID to "DEFAULT_MEMBER_GROUP_ID",
                    *convertPublicKeys(keyEncodingService).toTypedArray(),
                    *convertEndpoints().toTypedArray(),
                    *convertTestObjects().toTypedArray(),
                    *createInvalidListFormat().toTypedArray(),
                    SOFTWARE_VERSION to "5.0.0",
                    PLATFORM_VERSION to "10",
                    SERIAL to "1",
                    DUMMY_KEY to "dummyValue",
                    NULL_KEY to null
                ),
                converter
            ),
            mgmProvidedContext = MGMContextImpl(
                sortedMapOf(
                    STATUS to MEMBER_STATUS_ACTIVE,
                    MODIFIED_TIME to modifiedTime.toString(),
                    DUMMY_KEY to "dummyValue"
                ),
                converter
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

        private fun convertPublicKeys(keyEncodingService: KeyEncodingService): List<Pair<String, String>> =
            identityKeys.mapIndexed { i, identityKey -> String.format(IDENTITY_KEYS_KEY, i) to keyEncodingService.encodeAsString(identityKey) }

        private fun convertTestObjects(): List<Pair<String, String>> {
            val result = mutableListOf<Pair<String, String>>()
            for(i in testObjects.indices) {
                result.add((Pair(String.format(TEST_OBJECT_NUMBER, i), testObjects[i].number.toString())))
                result.add((Pair(String.format(TEST_OBJECT_TEXT, i), testObjects[i].text)))
            }
            return result
        }

        private fun createInvalidListFormat(): List<Pair<String, String>> = listOf(Pair("$INVALID_LIST_KEY.value", "invalidValue"))

        val MemberInfo.testObjects: List<TestObject>
            get() = this.memberProvidedContext.parseList("custom.testObjects")

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
                MemberContextImpl(user.memberContext.toSortedMap(), converter),
                MGMContextImpl(user.mgmContext.toSortedMap(), converter)
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
        assertEquals(memberInfo?.status, recreatedMemberInfo?.status)
        assertEquals(memberInfo?.groupId, recreatedMemberInfo?.groupId)
    }

    @Test
    fun `extension function and parser to retrieve list of custom objects works`() {
        assertEquals(testObjects, memberInfo?.testObjects)
    }

    @Test
    fun `parsing of a list returns back emptyList when no value is found`() {
        assertTrue {
            memberInfo?.memberProvidedContext?.parseList<String>("dummyKey")?.isEmpty()!!
        }
    }

    @Test
    fun `convert fails when non-existing key is used`() {
        val nonExistentKey = "nonExistentKey"
        val ex = assertFailsWith<ValueNotFoundException> { memberInfo?.mgmProvidedContext?.parse(nonExistentKey) }
        assertEquals("There is no value for '$nonExistentKey' key.", ex.message)
    }

    @Test
    fun `parse throws error when value is null for a key`() {
        val ex = assertFailsWith<IllegalStateException> { memberInfo?.memberProvidedContext?.parse("nullKey") }
        assertEquals("Converted value cannot be null.", ex.message)
        assertNull(memberInfo?.memberProvidedContext?.parseOrNull("nullKey"))
    }

    @Test
    fun `retrieving value from cache fails when casting is impossible`() {
        val keys = memberInfo?.identityKeys
        assertEquals(identityKeys, keys)
        assertFailsWith<ClassCastException> { memberInfo?.memberProvidedContext?.parse(IDENTITY_KEYS) as EndpointInfo }
    }

    @Test
    fun `convert fails when there is no converter for the required type`() {
        val ex = assertFailsWith<IllegalStateException> { memberInfo?.mgmProvidedContext?.parse(DUMMY_KEY) as DummyObject }
        assertEquals("Unknown 'net.corda.membership.identity.DummyObject' type.", ex.message)
    }

    @Test
    fun `parsing of list fails when the formatting of key is incorrect`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            memberInfo?.dummy
        }
        assertEquals("Prefix is invalid, only number is accepted after prefix.", ex.message)
    }
}

data class DummyObject(val text: String)

data class TestObject(val number: Int, val text: String)

@Component(service = [CustomPropertyConverter::class])
class TestStringConverter: CustomPropertyConverter<TestObject> {
    override val type: Class<TestObject>
        get() = TestObject::class.java

    override fun convert(context: ConversionContext): TestObject {
        return TestObject(
            context.store["number"]?.toInt() ?: throw NullPointerException(),
            context.store["text"] ?: throw NullPointerException()
        )
    }
}