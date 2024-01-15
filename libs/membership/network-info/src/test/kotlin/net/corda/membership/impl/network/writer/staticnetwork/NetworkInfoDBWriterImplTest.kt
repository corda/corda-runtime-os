package net.corda.membership.impl.network.writer.staticnetwork

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.KeyValuePairList
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MPV_KEY
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_STATIC_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.STATIC_NETWORK
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.MGM_INFO
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.PROTOCOL_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.grouppolicy.MemberGroupPolicy
import net.corda.membership.lib.toMap
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSignatureSpec
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager

class NetworkInfoDBWriterImplTest {

    private companion object {
        const val CUSTOM_KEY = "ext.key"
        const val CUSTOM_VALUE = "value"
        const val MPV = "50000"
    }

    private val groupId = UUID(0, 1).toString()
    private val platformInfoProvider: PlatformInfoProvider = mock {
        on { activePlatformVersion } doReturn 9999
        on { localWorkerSoftwareVersion } doReturn "software-version"
    }
    private val serializedProperties = byteArrayOf(0, 1, 2)
    private val serializer: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(any()) } doReturn serializedProperties
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn serializer
    }
    private val encodedMgmPublicKey = "mgm-public-key".toByteArray()
    private val stringEncodedMgmPublicKey = "mgm-public-key"
    private val mgmPublicKey = mock<PublicKey> {
        on { encoded } doReturn encodedMgmPublicKey
    }
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(encodedMgmPublicKey) } doReturn mgmPublicKey
        on { encodeAsString(mgmPublicKey) } doReturn stringEncodedMgmPublicKey
    }

    private val clock = TestClock(Instant.ofEpochSecond(1))
    private val mockProtocolParameters = mock<GroupPolicy.ProtocolParameters>()
    private val mockGroupPolicy = mock<MemberGroupPolicy> {
        on { groupId } doReturn groupId
        on { protocolParameters } doReturn mockProtocolParameters
    }
    private val groupPolicyParser = mock<GroupPolicyParser> {
        on { parseMember(any()) } doReturn mockGroupPolicy
    }

    private val staticNetworkInfoDBWriterImpl = NetworkInfoDBWriterImpl(
        clock,
        platformInfoProvider,
        keyEncodingService,
        groupPolicyParser,
        cordaAvroSerializationFactory
    )

    private val existingInfo: StaticNetworkInfoEntity = mock {
        on { mgmPublicKey } doReturn encodedMgmPublicKey
    }
    private val entityManager: EntityManager = mock()

    private fun getGroupPolicy(
        isStatic: Boolean = true,
        hasGroupId: Boolean = true
    ): String {
        val mapper = ObjectMapper()

        val protocolParameters: ObjectNode = mapper.createObjectNode().also {
            if (isStatic) {
                it.set<ObjectNode?>(STATIC_NETWORK, mapper.createObjectNode())
            }
        }

        val groupPolicy: ObjectNode = mapper
            .createObjectNode()
            .set<ObjectNode?>(PROTOCOL_PARAMETERS, protocolParameters).also {
                if (hasGroupId) {
                    it.put(GROUP_ID, groupId)
                }
            }

        return groupPolicy.toString()
    }

    @Nested
    inner class ParseAndPersistStaticNetworkInfo {
        private val metadata: CpiMetadata = mock {
            on { groupPolicy } doReturn getGroupPolicy()
        }
        private val cpi: Cpi = mock {
            on { metadata } doReturn metadata
        }

        @Test
        fun `Can parse and persist static network info`() {
            val groupParamsFromPolicy = mapOf(
                MPV_KEY to MPV,
                CUSTOM_KEY to CUSTOM_VALUE
            )
            whenever(mockProtocolParameters.staticNetworkGroupParameters).doReturn(groupParamsFromPolicy)
            val captor = argumentCaptor<KeyValuePairList>()
            whenever(serializer.serialize(captor.capture())).doReturn(serializedProperties)

            val result = assertDoesNotThrow {
                staticNetworkInfoDBWriterImpl.parseAndPersistStaticNetworkInfo(entityManager, cpi)
            }

            assertThat(result).isNotNull
            assertThat(result!!.groupId).isEqualTo(groupId)
            assertThat(result.groupParameters).isEqualTo(serializedProperties)
            assertThat(captor.firstValue.toMap()).containsExactlyInAnyOrderEntriesOf(
                groupParamsFromPolicy + mapOf(
                    EPOCH_KEY to "1",
                    MODIFIED_TIME_KEY to clock.instant().toString()
                )
            )

            val keyFactory = KeyFactory.getInstance(
                StaticNetworkUtils.mgmSigningKeyAlgorithm,
                StaticNetworkUtils.mgmSigningKeyProvider
            )
            assertDoesNotThrow { keyFactory.generatePublic(X509EncodedKeySpec(result.mgmPublicKey)) }
            assertDoesNotThrow { keyFactory.generatePrivate(PKCS8EncodedKeySpec(result.mgmPrivateKey)) }

            verify(entityManager).persist(result)
        }

        @Test
        fun `Do nothing if group policy is absent`() {
            whenever(metadata.groupPolicy).doReturn(null)
            val result = assertDoesNotThrow {
                staticNetworkInfoDBWriterImpl.parseAndPersistStaticNetworkInfo(entityManager, cpi)
            }
            assertThat(result).isNull()
            verify(entityManager, never()).persist(any())
        }

        @Test
        fun `Do nothing if not a static network group policy`() {
            whenever(metadata.groupPolicy).doReturn(getGroupPolicy(false))
            val result = assertDoesNotThrow {
                staticNetworkInfoDBWriterImpl.parseAndPersistStaticNetworkInfo(entityManager, cpi)
            }
            assertThat(result).isNull()
            verify(entityManager, never()).persist(any())
        }

        @Test
        fun `existing info is returned if already existing`() {
            whenever(entityManager.find(StaticNetworkInfoEntity::class.java, groupId))
                .doReturn(existingInfo)
            val result = assertDoesNotThrow {
                staticNetworkInfoDBWriterImpl.parseAndPersistStaticNetworkInfo(entityManager, cpi)
            }
            assertThat(result).isEqualTo(existingInfo)
            verify(entityManager, never()).persist(any())
        }

        @Test
        fun `exception thrown if group parameters cannot be serialized`() {
            whenever(serializer.serialize(any())).doReturn(null)
            assertThrows<CordaRuntimeException> {
                staticNetworkInfoDBWriterImpl.parseAndPersistStaticNetworkInfo(entityManager, cpi)
            }.also {
                assertThat(it.message).contains("Failed to serialize KeyValuePairList")
            }
            verify(entityManager, never()).persist(any())
        }
    }

    @Nested
    inner class InjectStaticNetworkMgm {
        @Test
        fun `can inject static network MGM`() {
            whenever(entityManager.find(StaticNetworkInfoEntity::class.java, groupId)).doReturn(existingInfo)
            val groupPolicyStr = getGroupPolicy()
            val result = assertDoesNotThrow {
                staticNetworkInfoDBWriterImpl.injectStaticNetworkMgm(entityManager, groupPolicyStr)
            }
            assertThat(result).isNotEqualTo(groupPolicyStr)

            val groupPolicyJson = ObjectMapper().readTree(result)
            assertThat(groupPolicyJson.has(MGM_INFO)).isTrue

            val mgmInfo = groupPolicyJson.get(MGM_INFO)
            assertThat(mgmInfo.get(PARTY_NAME).textValue()).contains("Corda-Static-Network-MGM")
            assertThat(mgmInfo.get(PARTY_SESSION_KEYS.format(0)).textValue()).isEqualTo(stringEncodedMgmPublicKey)
            assertThat(
                mgmInfo.get(SESSION_KEYS_SIGNATURE_SPEC.format(0)).textValue()
            ).isEqualTo(mgmSignatureSpec.signatureName)
            assertThat(mgmInfo.get(PLATFORM_VERSION).textValue()).isEqualTo("9999")
            assertThat(mgmInfo.get(SOFTWARE_VERSION).textValue()).isEqualTo("software-version")
            assertThat(mgmInfo.get(MemberInfoExtension.Companion.GROUP_ID).textValue()).isEqualTo(groupId)
            assertThat(mgmInfo.get(IS_STATIC_MGM).textValue()).isEqualTo("true")
        }

        @Test
        fun `Do nothing if not a static network group policy`() {
            val groupPolicy = getGroupPolicy(isStatic = false)
            val result = assertDoesNotThrow {
                staticNetworkInfoDBWriterImpl.injectStaticNetworkMgm(entityManager, groupPolicy)
            }
            assertThat(result).isEqualTo(groupPolicy)
            assertThat(result).isSameAs(groupPolicy)
        }

        @Test
        fun `throw exception if group ID is not in group policy`() {
            assertThrows<CordaRuntimeException> {
                staticNetworkInfoDBWriterImpl.injectStaticNetworkMgm(entityManager, getGroupPolicy(hasGroupId = false))
            }.also {
                assertThat(it.message).contains("group policy ID not found")
            }
        }

        @Test
        fun `throw exception if static network info doesn't exist`() {
            whenever(entityManager.find(StaticNetworkInfoEntity::class.java, groupId)).doReturn(null)
            assertThrows<CordaRuntimeException> {
                staticNetworkInfoDBWriterImpl.injectStaticNetworkMgm(entityManager, getGroupPolicy())
            }.also {
                assertThat(it.message).contains("Could not find existing static network")
            }
        }
    }
}
