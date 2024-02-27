package net.corda.membership.impl.registration.staticnetwork

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.StaticNetworkInfo
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.GroupParametersNotaryUpdater
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.toMap
import net.corda.membership.network.writer.staticnetwork.StaticNetworkInfoMappingUtils.toCorda
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSignatureSpec
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSigningKeyProvider
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.utilities.time.Clock
import net.corda.v5.membership.MemberInfo
import java.nio.ByteBuffer
import java.security.Signature

object StaticNetworkGroupParametersUtils {
    /**
     * Adds a notary to the group parameters. Adds new (or rotated) notary keys if the specified notary service exists,
     * or creates a new notary service.
     *
     * @param notary Notary to be added.
     *
     * @return Updated group parameters with notary information.
     */
    fun KeyValuePairList.addNotary(
        notary: MemberInfo,
        currentProtocolVersions: Collection<Int>,
        keyEncodingService: KeyEncodingService,
        clock: Clock
    ): KeyValuePairList? {
        val deserializedParams = toMap()

        val notaryDetails = notary.notaryDetails
            ?: throw MembershipRegistrationException(
                "Cannot add notary information - '${notary.name}' does not have role set to 'notary'."
            )

        val notaryServiceNumber = deserializedParams
            .entries
            .firstOrNull {
                it.value == notaryDetails.serviceName.toString()
            }?.run {
                GroupParametersNotaryUpdater.notaryServiceRegex.find(key)?.groups?.get(1)?.value?.toIntOrNull()
            }

        val notaryUpdater = GroupParametersNotaryUpdater(keyEncodingService, clock)
        return if (notaryServiceNumber != null) {
            notaryUpdater.updateExistingNotaryService(
                deserializedParams,
                notaryDetails,
                notaryServiceNumber,
                currentProtocolVersions
            ).second
        } else {
            notaryUpdater.addNewNotaryService(
                deserializedParams,
                notaryDetails,
            ).second
        }
    }

    fun StaticNetworkInfo.signGroupParameters(
        serializer: CordaAvroSerializer<KeyValuePairList>,
        keyEncodingService: KeyEncodingService,
        groupParametersFactory: GroupParametersFactory
    ): SignedGroupParameters {
        val serializedParams = serializer.serialize(groupParameters)

        val staticNetworkInfo = this.toCorda(groupParametersFactory)

        val signature = Signature.getInstance(mgmSignatureSpec.signatureName, mgmSigningKeyProvider)
        signature.initSign(staticNetworkInfo.mgmSigningPrivateKey)
        signature.update(serializedParams)

        return SignedGroupParameters(
            ByteBuffer.wrap(serializedParams),
            CryptoSignatureWithKey(
                ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(staticNetworkInfo.mgmSigningPublicKey)),
                ByteBuffer.wrap(signature.sign())
            ),
            CryptoSignatureSpec(mgmSignatureSpec.signatureName, null, null)
        )
    }
}
