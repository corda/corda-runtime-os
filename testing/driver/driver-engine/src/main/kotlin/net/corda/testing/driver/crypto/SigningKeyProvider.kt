package net.corda.testing.driver.crypto

import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.UUID
import java.util.Collections.singletonList
import java.util.Collections.unmodifiableMap
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullId
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.persistence.SigningKeyStatus
import net.corda.crypto.softhsm.PrivateKeyService
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE_FILTER
import net.corda.testing.driver.sandbox.CORDA_LOCAL_IDENTITY_PID
import net.corda.testing.driver.sandbox.CORDA_LOCAL_TENANCY_PID
import net.corda.testing.driver.sandbox.CORDA_MEMBER_COUNT
import net.corda.testing.driver.sandbox.CORDA_MEMBER_PRIVATE_KEY
import net.corda.testing.driver.sandbox.CORDA_MEMBER_PUBLIC_KEY
import net.corda.testing.driver.sandbox.CORDA_MEMBER_X500_NAME
import net.corda.testing.driver.sandbox.CORDA_TENANT
import net.corda.testing.driver.sandbox.CORDA_TENANT_COUNT
import net.corda.testing.driver.sandbox.CORDA_TENANT_MEMBER
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE
import org.osgi.service.component.annotations.Reference

@Component(
    service = [ SigningKeyProvider::class ],
    configurationPid = [ CORDA_LOCAL_IDENTITY_PID, CORDA_LOCAL_TENANCY_PID ],
    configurationPolicy = REQUIRE
)
class SigningKeyProvider @Activate constructor(
    @Reference
    schemeMetadata: CipherSchemeMetadata,
    @Reference(target = DRIVER_SERVICE_FILTER)
    privateKeyService: PrivateKeyService,
    properties: Map<String, Any>
) {
    private val cachedSigningKeys: Map<MemberX500Name, SigningKeyInfo>
    private val tenancy: Map<String, MemberX500Name>

    private fun calculateHash(key: PublicKey): ShortHash {
        return ShortHash.of(SecureHashImpl(DigestAlgorithmName.SHA2_256.name, key.sha256Bytes()))
    }

    init {
        val localKeys = linkedMapOf<MemberX500Name, SigningKeyInfo>()
        (properties[CORDA_MEMBER_COUNT] as? Int)?.also { localCount ->
            for (idx in 0 until localCount) {
                (properties["$CORDA_MEMBER_X500_NAME.$idx"] as? String)?.let(MemberX500Name::parse)?.also { localMember ->
                    (properties["$CORDA_MEMBER_PUBLIC_KEY.$idx"] as? ByteArray)?.let(schemeMetadata::decodePublicKey)?.let { publicKey ->
                        (properties["$CORDA_MEMBER_PRIVATE_KEY.$idx"] as? ByteArray)?.also { privateBytes ->
                            val alias = UUID.randomUUID().toString()
                            val keyScheme = schemeMetadata.findKeyScheme(publicKey)
                            val keyFactory = schemeMetadata.findKeyFactory(keyScheme)
                            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateBytes, publicKey.algorithm))
                            val privateKeyMaterial = privateKeyService.wrap(alias, privateKey)

                            localKeys[localMember] = SigningKeyInfo(
                                id = calculateHash(publicKey),
                                fullId = parseSecureHash(publicKey.fullId()),
                                tenantId = "",
                                category = "",
                                alias = alias,
                                hsmAlias = null,
                                publicKey = publicKey.encoded,
                                keyMaterial = privateKeyMaterial,
                                schemeCodeName = keyScheme.codeName,
                                masterKeyAlias = "master",
                                externalId = null,
                                encodingVersion = 1,
                                timestamp = Instant.now(),
                                hsmId = "",
                                status = SigningKeyStatus.NORMAL
                            )
                        }
                    }
                }
            }
        }
        cachedSigningKeys = unmodifiableMap(localKeys)

        val localTenancy = linkedMapOf<String, MemberX500Name>()
        (properties[CORDA_TENANT_COUNT] as? Int)?.also { localCount ->
            for (idx in 0 until localCount) {
                (properties["$CORDA_TENANT.$idx"] as? String)?.also { tenantId ->
                    (properties["$CORDA_TENANT_MEMBER.$idx"] as? String)?.let(MemberX500Name::parse)?.also { localMember ->
                        localTenancy[tenantId] = localMember
                    }
                }
            }
        }
        tenancy = unmodifiableMap(localTenancy)
    }

    @Suppress("unused_parameter")
    fun getSigningKey(tenantId: String, publicKey: PublicKey): SigningKeyInfo? {
        return tenancy[tenantId]?.let { localMember ->
            cachedSigningKeys[localMember]?.let { cached ->
                SigningKeyInfo(
                    id = cached.id,
                    fullId = cached.fullId,
                    tenantId = tenantId,
                    category = cached.category,
                    alias = cached.alias,
                    hsmAlias = cached.hsmAlias,
                    publicKey = cached.publicKey,
                    keyMaterial = cached.keyMaterial,
                    schemeCodeName = cached.schemeCodeName,
                    masterKeyAlias = cached.masterKeyAlias,
                    externalId = cached.externalId,
                    encodingVersion = cached.encodingVersion,
                    timestamp = cached.timestamp,
                    hsmId = cached.hsmId,
                    status = cached.status
                )
            }
        }
    }

    fun getSigningKeys(tenantId: String, fullKeyIds: List<SecureHash>): List<CryptoSigningKey> {
        return tenancy[tenantId]?.let { localMember ->
            cachedSigningKeys[localMember]?.takeIf { it.fullId in fullKeyIds }?.let { cached ->
                singletonList(CryptoSigningKey(
                    cached.id.toString(),
                    tenantId,
                    cached.category,
                    cached.alias,
                    cached.hsmAlias,
                    ByteBuffer.wrap(cached.publicKey).asReadOnlyBuffer(),
                    cached.schemeCodeName,
                    cached.masterKeyAlias,
                    cached.encodingVersion,
                    cached.externalId,
                    cached.timestamp
                ))
            }
        } ?: emptyList()
    }
}
