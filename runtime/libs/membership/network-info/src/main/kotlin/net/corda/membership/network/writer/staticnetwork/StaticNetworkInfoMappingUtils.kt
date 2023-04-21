package net.corda.membership.network.writer.staticnetwork

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.StaticNetworkInfo as AvroStaticNetworkInfo
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSigningKeyAlgorithm
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSigningKeyProvider
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

object StaticNetworkInfoMappingUtils {

    fun StaticNetworkInfoEntity.toAvro(
        deserializer: CordaAvroDeserializer<KeyValuePairList>
    ) = AvroStaticNetworkInfo(
        groupId,
        deserializer.deserialize(groupParameters),
        ByteBuffer.wrap(mgmPublicKey),
        ByteBuffer.wrap(mgmPrivateKey),
        version
    )

    fun AvroStaticNetworkInfo.toCorda(
        groupParametersFactory: GroupParametersFactory
    ): StaticNetworkInfo {
        val keyFactory = KeyFactory.getInstance(mgmSigningKeyAlgorithm, mgmSigningKeyProvider)

        return StaticNetworkInfo(
            UUID.fromString(groupId),
            keyFactory.generatePublic(X509EncodedKeySpec(mgmPublicSigningKey.array())),
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(mgmPrivateSigningKey.array())),
            groupParametersFactory.create(groupParameters),
            version
        )
    }
}