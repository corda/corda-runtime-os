package net.corda.membership.impl.staticnetwork

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.libs.packaging.Cpi
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.grouppolicy.GroupPolicyParser.Companion.groupIdFromJson
import net.corda.membership.lib.grouppolicy.GroupPolicyParser.Companion.isStaticNetwork
import net.corda.membership.staticnetwork.StaticNetworkInfoWriter
import net.corda.membership.staticnetwork.StaticNetworkUtils.mgmSigningKeyAlgorithm
import net.corda.membership.staticnetwork.StaticNetworkUtils.mgmSigningKeyProvider
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.security.KeyPairGenerator
import javax.persistence.EntityManager

@Component(service = [StaticNetworkInfoWriter::class])
class StaticNetworkInfoDBWriterImpl(
    private val clock: Clock,
    private val platformInfoProvider: PlatformInfoProvider,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : StaticNetworkInfoWriter {

    @Activate
    constructor(
        @Reference(service = PlatformInfoProvider::class)
        platformInfoProvider: PlatformInfoProvider,
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    ) : this(
        UTCClock(),
        platformInfoProvider,
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
}