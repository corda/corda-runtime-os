package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.PRE_AUTH
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.ShortHashException
import net.corda.crypto.core.fullId
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.impl.registration.RegistrationProxyImpl
import net.corda.membership.impl.registration.dynamic.mgm.ContextUtils.sessionKeyRegex
import net.corda.membership.lib.MemberInfoExtension.Companion.CREATION_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.ECDH_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.toWire
import net.corda.membership.p2p.helpers.KeySpecExtractor.Companion.validateSchemeAndSignatureSpec
import net.corda.membership.p2p.helpers.KeySpecExtractor.KeySpecType
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.PublicKey

@Suppress("LongParameterList")
internal class MGMRegistrationMemberInfoHandler(
    private val clock: Clock,
    private val cryptoOpsClient: CryptoOpsClient,
    private val keyEncodingService: KeyEncodingService,
    private val memberInfoFactory: MemberInfoFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val membershipQueryClient: MembershipQueryClient,
    private val platformInfoProvider: PlatformInfoProvider,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val keyIdList = listOf(SESSION_KEYS, ECDH_KEY_ID)
    }

    private val serializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            RegistrationProxyImpl.logger.error("Failed to serialize key value pair list.")
        }

    private fun serialize(context: KeyValuePairList): ByteArray {
        return serializer.serialize(context) ?: throw CordaRuntimeException(
            "Failed to serialize key value pair list."
        )
    }

    @Throws(MGMRegistrationMemberInfoHandlingException::class)
    fun persistMgmMemberInfo(holdingIdentity: HoldingIdentity, selfSignedMemberInfo: SelfSignedMemberInfo) {
        logger.info("Started persisting mgm member info.")
        val persistenceResult = membershipPersistenceClient.persistMemberInfo(holdingIdentity, listOf(selfSignedMemberInfo))
            .execute()
        if (persistenceResult is MembershipPersistenceResult.Failure) {
            throw MGMRegistrationMemberInfoHandlingException(
                "Registration failed, persistence error. Reason: ${persistenceResult.errorMsg}"
            )
        }
    }

    fun queryForMGMMemberInfo(holdingIdentity: HoldingIdentity): SelfSignedMemberInfo {
        return membershipQueryClient.queryMemberInfo(
            holdingIdentity,
            setOf(holdingIdentity),
            listOf(MEMBER_STATUS_ACTIVE)
        ).getOrThrow().single()
    }

    @Suppress("ThrowsCount")
    private fun getKeyFromId(
        keyId: String,
        tenantId: String,
        expectedCategory: String,
        signatureSpec: String? = null
    ): PublicKey {
        val parsedKeyId =
            try {
                ShortHash.parse(keyId)
            } catch (e: ShortHashException) {
                throw IllegalArgumentException(e)
            }
        return cryptoOpsClient.lookupKeysByIds(
            tenantId,
            listOf(parsedKeyId)
        ).firstOrNull()?.let {
            if (it.category != expectedCategory) {
                throw MGMRegistrationContextValidationException(
                    "Wrong key category. Key ID: $keyId category is ${it.category}. please use key from the $expectedCategory category.",
                    null
                )
            }
            if (expectedCategory == SESSION_INIT) {
                try {
                    it.validateSchemeAndSignatureSpec(signatureSpec, KeySpecType.SESSION)
                } catch (ex: IllegalArgumentException) {
                    throw MGMRegistrationContextValidationException(
                        "Key scheme and/or signature spec are not valid for category $SESSION_INIT.",
                        ex
                    )
                }
            }
            try {
                keyEncodingService.decodePublicKey(it.publicKey.array())
            } catch (ex: RuntimeException) {
                throw MGMRegistrationMemberInfoHandlingException(
                    "Could not decode public key for tenant ID: " +
                        "$tenantId under ID: $keyId.",
                    ex
                )
            }
        } ?: throw MGMRegistrationMemberInfoHandlingException(
            "No key found for tenant: $tenantId under ID: $keyId."
        )
    }

    private fun PublicKey.toPem(): String = keyEncodingService.encodeAsString(this)

    fun buildMgmMemberInfo(
        holdingIdentity: HoldingIdentity,
        context: Map<String, String>,
        serialNumber: Long = 1,
        creationTime: String? = null,
    ): SelfSignedMemberInfo {
        val cpi = virtualNodeInfoReadService.get(holdingIdentity)?.cpiIdentifier
            ?: throw MGMRegistrationMemberInfoHandlingException(
                "Could not find virtual node info for member ${holdingIdentity.shortHash}"
            )
        val ecdhKey = getKeyFromId(context[ECDH_KEY_ID]!!, holdingIdentity.shortHash.value, PRE_AUTH)
        if (ecdhKey.algorithm != "EC") {
            throw MGMRegistrationContextValidationException("ECDH key must be created with an EC schema.", null)
        }
        val now = clock.instant().toString()
        val optionalContext = mapOf(MEMBER_CPI_SIGNER_HASH to cpi.signerSummaryHash.toString())
        val sessionKeys = context.filterKeys { key ->
            sessionKeyRegex.matches(key)
        }.map {
            val keyIndex = it.key.substringAfter("$SESSION_KEYS.").substringBefore('.')
            val signatureSpec = context[SESSION_KEYS_SIGNATURE_SPEC.format(keyIndex)]
            getKeyFromId(it.value, holdingIdentity.shortHash.value, SESSION_INIT, signatureSpec)
        }.flatMapIndexed { index, sessionKey ->
            listOf(
                String.format(PARTY_SESSION_KEYS_PEM, index) to sessionKey.toPem(),
                String.format(SESSION_KEYS_HASH, index) to sessionKey.fullId(),
            )
        }
        val memberContext = context.filterKeys { key ->
            !keyIdList.any { keyPrefix ->
                key.startsWith(keyPrefix)
            }
        }.filterKeys {
            !it.startsWith(GROUP_POLICY_PREFIX_WITH_DOT)
        } + mapOf(
            GROUP_ID to holdingIdentity.groupId,
            PARTY_NAME to holdingIdentity.x500Name.toString(),
            ECDH_KEY to ecdhKey.toPem(),
            PLATFORM_VERSION to platformInfoProvider.activePlatformVersion.toString(),
            SOFTWARE_VERSION to platformInfoProvider.localWorkerSoftwareVersion,
            MEMBER_CPI_NAME to cpi.name,
            MEMBER_CPI_VERSION to cpi.version,
        ) + optionalContext + sessionKeys
        return memberInfoFactory.createSelfSignedMemberInfo(
            serialize(memberContext.toSortedMap().toWire()),
            serialize(
                sortedMapOf(
                    CREATION_TIME to (creationTime ?: now),
                    MODIFIED_TIME to now,
                    STATUS to MEMBER_STATUS_ACTIVE,
                    IS_MGM to "true",
                    SERIAL to serialNumber.toString(),
                ).toWire()
            ),
            CryptoSignatureWithKey(
                ByteBuffer.wrap(byteArrayOf()),
                ByteBuffer.wrap(byteArrayOf())
            ),
            CryptoSignatureSpec("", null, null),
        )
    }
}

internal class MGMRegistrationMemberInfoHandlingException(
    val reason: String,
    ex: Throwable? = null
) : CordaRuntimeException(reason, ex)
