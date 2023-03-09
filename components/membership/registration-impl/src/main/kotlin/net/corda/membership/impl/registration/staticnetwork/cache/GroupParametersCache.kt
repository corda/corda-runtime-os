package net.corda.membership.impl.registration.staticnetwork.cache

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.staticgroup.StaticGroupDefinition
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.NOTARY_SERVICE_NAME_KEY
import net.corda.membership.lib.addNewNotaryService
import net.corda.membership.lib.toMap
import net.corda.membership.lib.updateExistingNotaryService
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_STATIC_NETWORK_TOPIC
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap

class GroupParametersCache(
    private val platformInfoProvider: PlatformInfoProvider,
    private val publisher: Publisher,
    private val keyEncodingService: KeyEncodingService,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    val clock: Clock
) {
    constructor(
        platformInfoProvider: PlatformInfoProvider,
        publisher: Publisher,
        keyEncodingService: KeyEncodingService,
        cordaAvroSerializationFactory: CordaAvroSerializationFactory
    ):this(
        platformInfoProvider,
        publisher,
        keyEncodingService,
        cordaAvroSerializationFactory,
        UTCClock()
    )
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        val notaryServiceRegex = NOTARY_SERVICE_NAME_KEY.format("([0-9]+)").toRegex()

        const val SIGNATURE_SPEC = "corda.membership.signature.spec"
    }

    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList> {
        logger.error("Could not serialize key value pair list.")
    }
    private val deserializer = cordaAvroSerializationFactory.createAvroDeserializer(
        {
            logger.error("Could not serialize key value pair list.")
        },
        KeyValuePairList::class.java
    )

    private val cache = ConcurrentHashMap<String, SignedGroupParameters>()

    /**
     * Sets group parameters for the specified group. Typically used by an event processor to update the cache.
     */
    fun set(groupId: String, groupParameters: SignedGroupParameters) {
        cache[groupId] = groupParameters
    }

    /**
     * Retrieves group parameters for the specified holding identity. If group parameters do not exist, this method creates
     * the initial group parameters snapshot for the group and publishes them to Kafka before returning them.
     *
     * @param holdingIdentity Holding identity of the member requesting the group parameters.
     *
     * @return Group parameters for the group if present, or newly created snapshot of group parameters.
     */
    fun getOrCreateGroupParameters(holdingIdentity: HoldingIdentity): SignedGroupParameters =
        cache[holdingIdentity.groupId] ?: createGroupParametersSnapshot(holdingIdentity)

    /**
     * Adds a notary to the group parameters. Adds new (or rotated) notary keys if the specified notary service exists,
     * or creates a new notary service.
     *
     * @param notary Notary to be added.
     *
     * @return Updated group parameters with notary information.
     */
    fun addNotary(notary: MemberInfo): SignedGroupParameters? {
        val groupId = notary.groupId

        val deserializedParams = cache[groupId]?.let {
            deserializer.deserialize(it.groupParameters.array())?.toMap()
        } ?: throw MembershipRegistrationException("Cannot add notary information - no group parameters found.")

        val notaryDetails = notary.notaryDetails
            ?: throw MembershipRegistrationException(
                "Cannot add notary information - '${notary.name}' does not have role set to 'notary'."
            )

        val notaryServiceNumber = deserializedParams
            .entries
            .firstOrNull {
                it.value == notaryDetails.serviceName.toString()
            }?.run {
                notaryServiceRegex.find(key)?.groups?.get(1)?.value?.toIntOrNull()
            }

        val updated = if (notaryServiceNumber != null) {
            updateExistingNotaryService(
                deserializedParams,
                notaryDetails,
                notaryServiceNumber,
                keyEncodingService,
                logger,
                clock
            ).second
        } else {
            addNewNotaryService(
                deserializedParams,
                notaryDetails,
                keyEncodingService,
                logger,
                clock
            ).second
        }

        return updated?.let {
            it.sign().also { params ->
                params.publish(groupId)
            }
        }
    }

    private fun createGroupParametersSnapshot(holdingIdentity: HoldingIdentity): SignedGroupParameters {
        return KeyValuePairList(
            listOf(
                KeyValuePair(EPOCH_KEY, "1"),
                KeyValuePair(MPV_KEY, platformInfoProvider.activePlatformVersion.toString()),
                KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
            )
        ).sign()
            .apply {
                set(holdingIdentity.groupId, this)
                publish(holdingIdentity.groupId)
            }
    }

    private fun KeyValuePairList.sign(): SignedGroupParameters {
        val serializedParams = serializer.serialize(this)

        val keyGen = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())
        val keyPair = keyGen.generateKeyPair()

        val signer = Signature.getInstance("SHA256withRSA", BouncyCastleProvider())
        signer.initSign(keyPair.private)
        val signature = signer.sign()

        return SignedGroupParameters(
            ByteBuffer.wrap(serializedParams),
            CryptoSignatureWithKey(
                ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(keyPair.public)),
                ByteBuffer.wrap(signature),
                KeyValuePairList(listOf(KeyValuePair(SIGNATURE_SPEC, SignatureSpec.RSA_SHA256.signatureName)))
            )
        )
    }

    private fun SignedGroupParameters.publish(groupId: String) {
        publisher.publish(
            listOf(
                Record(
                    MEMBERSHIP_STATIC_NETWORK_TOPIC,
                    groupId,
                    StaticGroupDefinition(groupId, this)
                )
            )
        ).forEach { it.get() }
    }
}