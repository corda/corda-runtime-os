package net.corda.membership.impl.persistence.service.handler

import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberSignature
import net.corda.data.membership.db.response.query.MemberSignature
import net.corda.data.membership.db.response.query.MemberSignatureQueryResponse
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.db.lib.retrieveSignatureSpec
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

internal class QueryMemberSignatureHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<QueryMemberSignature, MemberSignatureQueryResponse>(persistenceHandlerServices) {

    override fun invoke(
        context: MembershipRequestContext,
        request: QueryMemberSignature,
    ): MemberSignatureQueryResponse {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            MemberSignatureQueryResponse(
                request.queryIdentities.mapNotNull { holdingIdentity ->
                    val memberInfoEntity = em.find(
                        MemberInfoEntity::class.java,
                        MemberInfoEntityPrimaryKey(
                            holdingIdentity.groupId,
                            holdingIdentity.x500Name,
                            false
                        )
                    ) ?: throw MembershipPersistenceException("Could not find signature for $holdingIdentity")

                    MemberSignature(
                        holdingIdentity,
                        CryptoSignatureWithKey(
                            ByteBuffer.wrap(memberInfoEntity.memberSignatureKey),
                            ByteBuffer.wrap(memberInfoEntity.memberSignatureContent)
                        ),
                        retrieveSignatureSpec(memberInfoEntity.memberSignatureSpec)
                    )
                }
            )
        }
    }
}
