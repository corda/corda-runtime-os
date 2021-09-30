package net.corda.membership.impl

import net.corda.crypto.testkit.CryptoMocks
import net.corda.data.identity.WireMemberInfo
import net.corda.membership.impl.MemberInfoExtension.Companion.CERTIFICATE
import net.corda.membership.impl.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.impl.MemberInfoExtension.Companion.IDENTITY_KEYS_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.impl.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.impl.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.STATUS
import net.corda.membership.impl.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.endpoints
import net.corda.membership.impl.MemberInfoExtension.Companion.modifiedTime
import net.corda.v5.application.node.EndpointInfo
import net.corda.v5.application.node.MemberInfo
import net.corda.v5.application.node.convertToListOfWireKeyValuePair
import net.corda.v5.cipher.suite.KeyEncodingService
import org.apache.avro.file.DataFileReader
import org.apache.avro.file.DataFileWriter
import org.apache.avro.io.DatumReader
import org.apache.avro.io.DatumWriter
import org.apache.avro.specific.SpecificDatumReader
import org.apache.avro.specific.SpecificDatumWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant
import java.util.*

class MemberInfoTest {
    companion object {
        private val cryptoMocks = CryptoMocks()
        private val keyEncodingService = cryptoMocks.schemeMetadata()
        private val cryptoService = cryptoMocks.cryptoService()
        private val alias = UUID.randomUUID().toString()
        private val signatureScheme = cryptoService.supportedSchemes().first()

        private val modifiedTime = Instant.now()
        private val endpoints = listOf(EndpointInfoImpl("https://localhost:10000", 1), EndpointInfoImpl("https://google.com", 10))
        private val identityKeys = listOf(cryptoService.generateKeyPair(alias, signatureScheme), cryptoService.generateKeyPair(alias, signatureScheme))
        private val memberInfo = createDummyMemberInfo()

        private fun createDummyMemberInfo(): MemberInfo = MemberInfoImpl(
            memberProvidedContext = MemberContextImpl(
                sortedMapOf(
                    PARTY_NAME to "O=Alice,L=London,C=GB",
                    PARTY_KEY to keyEncodingService.encodeAsString(identityKeys.first()),
                    GROUP_ID to "DEFAULT_MEMBER_GROUP_ID",
                    *convertPublicKeys(keyEncodingService).toTypedArray(),
                    *convertEndpoints(endpoints).toTypedArray(),
                    SOFTWARE_VERSION to "5.0.0",
                    PLATFORM_VERSION to "10",
                    SERIAL to "1",
                    CERTIFICATE to "dummy_cert_path"
                )
            ),
            mgmProvidedContext = MemberContextImpl(
                sortedMapOf(
                    STATUS to MEMBER_STATUS_ACTIVE,
                    MODIFIED_TIME to modifiedTime.toString()
                )
            ),
            keyEncodingService
        )

        private fun convertEndpoints(endpoints: List<EndpointInfo>): List<Pair<String, String>> {
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
    }

    @Test
    fun `serializing and deserializing WireMemberInfo using avro`() {
        var user: WireMemberInfo? = null
        val userDatumWriter: DatumWriter<WireMemberInfo> = SpecificDatumWriter(
            WireMemberInfo::class.java
        )
        val dataFileWriter: DataFileWriter<WireMemberInfo> = DataFileWriter(userDatumWriter)
        val wireMemberInfo = WireMemberInfo(
            memberInfo.memberProvidedContext.convertToListOfWireKeyValuePair(),
            memberInfo.mgmProvidedContext.convertToListOfWireKeyValuePair()
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
            recreatedMemberInfo = convertToMemberInfo(
                user.memberContext.convertToContext(),
                user.mgmContext.convertToContext(),
                keyEncodingService
            )
        }

        assertEquals(memberInfo, recreatedMemberInfo)
        assertEquals(memberInfo.identityKeys, recreatedMemberInfo?.identityKeys)
        assertEquals(memberInfo.party, recreatedMemberInfo?.party)
        assertEquals(memberInfo.endpoints, recreatedMemberInfo?.endpoints)
        assertEquals(memberInfo.modifiedTime, recreatedMemberInfo?.modifiedTime)
        assertEquals(memberInfo.isActive, recreatedMemberInfo?.isActive)
        assertEquals(memberInfo.serial, recreatedMemberInfo?.serial)
        assertEquals(memberInfo.platformVersion, recreatedMemberInfo?.platformVersion)
    }
}