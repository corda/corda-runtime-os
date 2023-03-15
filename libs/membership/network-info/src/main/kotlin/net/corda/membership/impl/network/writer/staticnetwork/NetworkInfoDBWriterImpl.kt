package net.corda.membership.impl.network.writer.staticnetwork

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.calculateHash
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.libs.packaging.Cpi
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_STATIC_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.MGM_INFO
import net.corda.membership.lib.grouppolicy.GroupPolicyParser.Companion.groupIdFromJson
import net.corda.membership.lib.grouppolicy.GroupPolicyParser.Companion.isStaticNetwork
import net.corda.membership.network.writer.NetworkInfoWriter
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSignatureSpec
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSigningKeyAlgorithm
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSigningKeyProvider
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.security.KeyPairGenerator
import javax.persistence.EntityManager

@Component(service = [NetworkInfoWriter::class])
class NetworkInfoDBWriterImpl(
    private val clock: Clock,
    private val platformInfoProvider: PlatformInfoProvider,
    private val keyEncodingService: KeyEncodingService,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : NetworkInfoWriter {

    @Activate
    constructor(
        @Reference(service = PlatformInfoProvider::class)
        platformInfoProvider: PlatformInfoProvider,
        @Reference(service = KeyEncodingService::class)
        keyEncodingService: KeyEncodingService,
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    ) : this(
        UTCClock(),
        platformInfoProvider,
        keyEncodingService,
        cordaAvroSerializationFactory
    )

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList> {
        logger.error("Failed to serialize KeyValuePairList.")
    }

    override fun parseAndPersistStaticNetworkInfo(
        em: EntityManager,
        cpi: Cpi
    ): StaticNetworkInfoEntity? {
        return cpi.metadata.groupPolicy?.takeIf {
            isStaticNetwork(it)
        }?.let { groupPolicy ->
            logger.info("Creating and persisting static network information.")

            val groupId = try {
                groupIdFromJson(groupPolicy)
            } catch (ex: CordaRuntimeException) {
                logger.error("Couldn't parse the group ID required to create the static network information.", ex)
                return@let null
            }

            if (em.find(StaticNetworkInfoEntity::class.java, groupId) != null) {
                logger.warn(
                    "Found existing configuration for static network [$groupId]. Skipping persistence of " +
                            "new configuration"
                )
                return@let null
            }

            val (encodedPublic, encodedPrivate) = KeyPairGenerator
                .getInstance(mgmSigningKeyAlgorithm, mgmSigningKeyProvider)
                .genKeyPair()
                .let { it.public.encoded to it.private.encoded }

            val serializedParams = serializer.serialize(
                KeyValuePairList(
                    listOf(
                        KeyValuePair(EPOCH_KEY, "1"),
                        KeyValuePair(MPV_KEY, platformInfoProvider.activePlatformVersion.toString()),
                        KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
                    )
                )
            ) ?: return@let null

            StaticNetworkInfoEntity(
                groupId,
                encodedPublic,
                encodedPrivate,
                serializedParams
            ).also { entity ->
                em.persist(entity)
            }
        }
    }

    override fun injectStaticNetworkMgm(
        em: EntityManager,
        groupPolicyJson: String
    ): String = if (!isStaticNetwork(groupPolicyJson)) {
        groupPolicyJson
    } else {
        try {
            val groupId = groupIdFromJson(groupPolicyJson)
            val staticNetworkInfo = em.find(
                StaticNetworkInfoEntity::class.java,
                groupId
            ) ?: throw CordaRuntimeException(
                "Could not find existing static network configuration persisted."
            )
            val mapper = ObjectMapper()

            val groupPolicy = mapper.readTree(groupPolicyJson) as ObjectNode

            val mgmSessionKey = keyEncodingService.decodePublicKey(staticNetworkInfo.mgmPublicKey)
            val staticMgmInfo = mapOf(
                PARTY_NAME to "O=Corda-Static-Network-MGM, OU=$groupId, L=London, C=GB",
                PARTY_SESSION_KEY to keyEncodingService.encodeAsString(mgmSessionKey),
                SESSION_KEY_HASH to mgmSessionKey.calculateHash().value,
                SESSION_KEY_SIGNATURE_SPEC to mgmSignatureSpec.signatureName,
                URL_KEY.format(0) to "https://localhost:8080",
                PROTOCOL_VERSION.format(0) to "1",
                PLATFORM_VERSION to platformInfoProvider.activePlatformVersion.toString(),
                SOFTWARE_VERSION to platformInfoProvider.localWorkerSoftwareVersion,
                GROUP_ID to groupId,
                IS_STATIC_MGM to true.toString()
            )

            val newMgmInfo = if (groupPolicy.has(MGM_INFO)) {
                mapper.convertValue(
                    groupPolicy.get(MGM_INFO),
                    object : TypeReference<Map<String, String>>() {}
                ) + staticMgmInfo
            } else {
                staticMgmInfo
            }
            groupPolicy.replace(MGM_INFO, mapper.readTree(mapper.writeValueAsString(newMgmInfo)))

            mapper.writeValueAsString(groupPolicy)
        } catch (ex: Exception) {
            logger.warn("Failed to inject static network MGM info into uploaded CPI group policy file.", ex)
            groupPolicyJson
        }
    }
}