package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberSignature
import net.corda.data.membership.db.response.query.MemberSignature
import net.corda.data.membership.db.response.query.MemberSignatureQueryResponse
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.MemberSignatureEntity
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

internal class QueryMemberSignatureHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<QueryMemberSignature, MemberSignatureQueryResponse>(persistenceHandlerServices) {
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: QueryMemberSignature,
    ): MemberSignatureQueryResponse {
        return transaction(context.holdingIdentity.toCorda().id) { em ->
            MemberSignatureQueryResponse(
                request.queryIdentities.mapNotNull { holdingIdentity ->
                    val signatureEntity = em.find(
                        MemberSignatureEntity::class.java,
                        MemberInfoEntityPrimaryKey(
                            holdingIdentity.groupId,
                            holdingIdentity.x500Name
                        )
                    ) ?: throw CordaRuntimeException("Could not find signature for $holdingIdentity")
                    val signatureContext = if (signatureEntity.context.isEmpty()) {
                        KeyValuePairList(emptyList())
                    } else {
                        keyValuePairListDeserializer.deserialize(signatureEntity.context)
                    }
                    val signature = CryptoSignatureWithKey(
                        ByteBuffer.wrap(signatureEntity.publicKey),
                        ByteBuffer.wrap(signatureEntity.content),
                        signatureContext,
                    )
                    MemberSignature(
                        holdingIdentity, signature
                    )
                }
            )
        }
    }
}
