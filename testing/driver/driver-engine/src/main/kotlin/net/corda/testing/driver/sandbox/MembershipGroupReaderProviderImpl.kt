package net.corda.testing.driver.sandbox

import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.impl.toMap
import net.corda.crypto.softhsm.PrivateKeyService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_STATIC_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE_FILTER
import net.corda.testing.driver.DriverConstants.DRIVER_SERVICE_RANKING
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName.SHA2_256
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MemberInfo
import net.corda.v5.membership.NotaryInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory

@Suppress("LongParameterList", "unused")
@Component(
    service = [ MembershipGroupReaderProvider::class ],
    configurationPid = [ CORDA_MEMBERSHIP_PID, CORDA_GROUP_PID ],
    configurationPolicy = REQUIRE,
    property = [ DRIVER_SERVICE ]
)
@ServiceRanking(DRIVER_SERVICE_RANKING)
class MembershipGroupReaderProviderImpl @Activate constructor(
    @Reference
    platformInfo: PlatformInfoProvider,
    @Reference(target = DRIVER_SERVICE_FILTER)
    private val groupPolicyProvider: GroupPolicyProvider,
    @Reference(target = DRIVER_SERVICE_FILTER)
    private val cryptoService: CryptoService,
    @Reference(target = DRIVER_SERVICE_FILTER)
    private val privateKeyService: PrivateKeyService,
    @Reference
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference
    private val groupParametersFactory: GroupParametersFactory,
    @Reference
    private val memberInfoFactory: MemberInfoFactory,
    @Reference
    layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    properties: Map<String, Any?>
) : MembershipGroupReaderProvider {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val baseMemberContext = sortedMapOf<String, String?>(
        PLATFORM_VERSION to platformInfo.activePlatformVersion.toString(),
        PROTOCOL_VERSION.format(0) to "1",
        SOFTWARE_VERSION to "000",
        URL_KEY.format(0) to "https://localhost:8080"
    )
    private val mgmContext = sortedMapOf<String, String?>(
        IS_STATIC_MGM to true.toString(),
        STATUS to MEMBER_STATUS_ACTIVE,
        SERIAL to "-1"
    )

    private val keyValuePairListSerializer = cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList> {}
    private val keyScheme = schemeMetadata.findKeyScheme(DEFAULT_KEY_SCHEME)
    private val signatureSpec = schemeMetadata.supportedSignatureSpec(keyScheme, SHA2_256).first()
    private val membership = parseNetwork(properties, schemeMetadata)
    private val groupParameters = parseGroupParameters(properties)
    private val notaries = layeredPropertyMapFactory.createMap(groupParameters.toMap())
        .parseList("corda.notary.service.", NotaryInfo::class.java)
        .associateBy(NotaryInfo::getName)

    private val groupReaders = ConcurrentHashMap<HoldingIdentity, MembershipGroupReader>()

    private fun getSigningSpecFor(publicKey: PublicKey): SigningWrappedSpec {
        val wrappedKey = requireNotNull(privateKeyService.fetchFor(publicKey)) {
            "Wrapped key missing for $publicKey"
        }
        val keyMaterialSpec = KeyMaterialSpec(
            keyMaterial = wrappedKey.keyMaterial,
            wrappingKeyAlias = WRAPPING_KEY_ALIAS,
            encodingVersion = 1
        )
        return SigningWrappedSpec(
            keyMaterialSpec,
            publicKey,
            keyScheme,
            signatureSpec
        )
    }

    private fun createSignedGroupParameters(holdingIdentity: HoldingIdentity): SignedGroupParameters {
        val mgmInfo = requireNotNull(groupPolicyProvider.getGroupPolicy(holdingIdentity)?.mgmInfo) {
            "MGM information missing for $holdingIdentity"
        }

        val signingSpec = getSigningSpecFor(
            schemeMetadata.decodePublicKey(
                requireNotNull(mgmInfo[FIRST_SESSION_KEY]) {
                    "Session Init key missing for $holdingIdentity"
                }
            )
        )

        val parameterBytes = requireNotNull(keyValuePairListSerializer.serialize(KeyValuePairList(groupParameters))) {
            "Cannot serialize group parameter key/value pairs for $holdingIdentity"
        }

        val signature = cryptoService.sign(signingSpec, parameterBytes, mgmInfo)
        val signatureWithKey = DigitalSignatureWithKey(signingSpec.publicKey, signature)
        return groupParametersFactory.create(parameterBytes, signatureWithKey, signatureSpec)
    }

    private fun parseNotaryInfo(notary: NotaryInfo): Map<String, String> {
        return linkedMapOf<String, String>().apply {
            this["${ROLES_PREFIX}.0"] = NOTARY_ROLE
            this[NOTARY_SERVICE_NAME] = notary.name.toString()
            this[NOTARY_SERVICE_PROTOCOL] = notary.protocol
            notary.protocolVersions.forEachIndexed { idx, version ->
                if (version != null) {
                    this[NOTARY_SERVICE_PROTOCOL_VERSIONS.format(idx)] = version.toString()
                }
            }
            notary.publicKey.also { publicKey ->
                this[NOTARY_KEY_PEM.format(0)] = schemeMetadata.encodeAsString(publicKey)
                this[NOTARY_KEY_HASH.format(0)] = publicKey.fullIdHash().toString()
                schemeMetadata.defaultSignatureSpec(publicKey)?.also { spec ->
                    this[NOTARY_KEY_SPEC.format(0)] = spec.signatureName
                }
            }
        }
    }

    override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader {
        return groupReaders.computeIfAbsent(holdingIdentity) { hid ->
            val membershipInfo = membership.mapValues { member ->
                val memberContext = sortedMapOf<String, String?>().apply {
                    this[PARTY_NAME] = member.key.toString()
                    this[GROUP_ID] = hid.groupId
                    this[LEDGER_KEYS_KEY.format(0)] = schemeMetadata.encodeAsString(member.value)
                    this += baseMemberContext

                    notaries[member.key]?.also { notary ->
                        this += parseNotaryInfo(notary)
                    }
                }
                memberInfoFactory.create(memberContext, mgmContext)
            }
            MembershipGroupReaderImpl(
                createSignedGroupParameters(hid),
                hid,
                membershipInfo
            )
        }
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
        logger.info("Started")
    }

    override fun stop() {
        logger.info("Stopped")
    }

    private fun parseGroupParameters(properties: Map<String, Any?>): List<KeyValuePair> {
        return (properties[CORDA_GROUP_PARAMETERS] as? ByteArray)?.let { bytes ->
            KeyValuePairList.fromByteBuffer(ByteBuffer.wrap(bytes)).items
        } ?: emptyList()
    }

    private fun parseNetwork(
        properties: Map<String, Any?>,
        schemeMetadata: CipherSchemeMetadata
    ): Map<MemberX500Name, PublicKey> {
        return buildMap {
            val memberCount = properties[CORDA_MEMBER_COUNT] as? Int ?: 0
            for (idx in 0 until memberCount) {
                val memberX500Name = (properties["$CORDA_MEMBER_X500_NAME.$idx"] as? String)?.let(MemberX500Name::parse)
                val publicKey = (properties["$CORDA_MEMBER_PUBLIC_KEY.$idx"] as? ByteArray)?.let(schemeMetadata::decodePublicKey)

                if (memberX500Name != null && publicKey != null) {
                    this[memberX500Name] = publicKey
                }
            }
        }
    }

    private class MembershipGroupReaderImpl(
        override val groupParameters: InternalGroupParameters?,
        holdingIdentity: HoldingIdentity,
        private val membershipInfo: Map<MemberX500Name, MemberInfo>
    ) : MembershipGroupReader {
        override val groupId: String = holdingIdentity.groupId

        override val owningMember: MemberX500Name = holdingIdentity.x500Name

        override val signedGroupParameters: SignedGroupParameters?
            get() = groupParameters as? SignedGroupParameters

        override fun lookup(filter: MembershipStatusFilter): Collection<MemberInfo> {
            return membershipInfo.values
        }

        override fun lookupByLedgerKey(ledgerKeyHash: SecureHash, filter: MembershipStatusFilter): MemberInfo? {
            return null
        }

        override fun lookup(name: MemberX500Name, filter: MembershipStatusFilter): MemberInfo? {
            return membershipInfo[name]
        }

        override fun lookupBySessionKey(sessionKeyHash: SecureHash, filter: MembershipStatusFilter): MemberInfo? {
            return null
        }

        override val notaryVirtualNodeLookup: NotaryVirtualNodeLookup = NotaryVirtualNodeLookupImpl()

        private inner class NotaryVirtualNodeLookupImpl: NotaryVirtualNodeLookup {
            override fun getNotaryVirtualNodes(notaryServiceName: MemberX500Name): List<MemberInfo> {
                return lookup().filter { memberInfo ->
                    memberInfo.notaryDetails?.serviceName == notaryServiceName
                }.sortedBy(MemberInfo::getName)
            }
        }
    }
}
