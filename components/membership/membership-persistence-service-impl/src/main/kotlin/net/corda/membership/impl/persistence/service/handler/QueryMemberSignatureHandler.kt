package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberSignature
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda

/**
 * Keeping this handler until we completely remove the [QueryMemberSignature] command.
 * Converting the reply to a [MemberInfoQueryResponse] for now.
 */
internal class QueryMemberSignatureHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<QueryMemberSignature, MemberInfoQueryResponse>(persistenceHandlerServices) {
    override val operation = QueryMemberSignature::class.java
    override fun invoke(
        context: MembershipRequestContext,
        request: QueryMemberSignature,
    ): MemberInfoQueryResponse {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            MemberInfoQueryResponse(
                request.queryIdentities.mapNotNull { holdingIdentity ->
                    val memberInfoEntity = em.find(
                        MemberInfoEntity::class.java,
                        MemberInfoEntityPrimaryKey(
                            holdingIdentity.groupId,
                            holdingIdentity.x500Name,
                            false
                        )
                    ) ?: throw MembershipPersistenceException("Could not find signature for $holdingIdentity")

                    memberInfoEntity.toPersistentMemberInfo(context.holdingIdentity)
                }
            )
        }
    }

    private fun MemberInfoEntity.toPersistentMemberInfo(viewOwningMember: net.corda.data.identity.HoldingIdentity) =
        memberInfoFactory.createPersistentMemberInfo(
            viewOwningMember,
            memberContext,
            mgmContext,
            memberSignatureKey,
            memberSignatureContent,
            memberSignatureSpec,
        )
}
