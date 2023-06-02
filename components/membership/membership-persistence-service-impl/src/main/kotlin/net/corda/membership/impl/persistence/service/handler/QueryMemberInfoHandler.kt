package net.corda.membership.impl.persistence.service.handler

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentSignedMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer
import javax.persistence.criteria.Predicate

internal class QueryMemberInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<QueryMemberInfo, MemberInfoQueryResponse>(persistenceHandlerServices) {
    @Suppress("SpreadOperator")
    override fun invoke(context: MembershipRequestContext, request: QueryMemberInfo): MemberInfoQueryResponse {
        val persistentSignedMemberInfos = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val criteriaBuilder = em.criteriaBuilder
            val memberQueryBuilder = criteriaBuilder.createQuery(MemberInfoEntity::class.java)
            val root = memberQueryBuilder.from(MemberInfoEntity::class.java)
            val predicates = mutableListOf<Predicate>()
            if (request.queryIdentities.isNotEmpty()) {
                logger.info("Querying MemberInfo(s) by name.")
                val inStatus = criteriaBuilder.`in`(root.get<String>("memberX500Name"))
                request.queryIdentities.forEach { queryIdentities ->
                    inStatus.value(queryIdentities.x500Name)
                }
                predicates.add(inStatus)
            }
            if (!request.queryStatuses.isNullOrEmpty()) {
                logger.info("Querying MemberInfo(s) by status.")
                val inStatus = criteriaBuilder.`in`(root.get<String>("status"))
                request.queryStatuses.forEach { queryStatus ->
                    inStatus.value(queryStatus.toString())
                }
                predicates.add(inStatus)
            }
            val memberQuery = memberQueryBuilder.select(root).where(*predicates.toTypedArray())
            em.createQuery(memberQuery).resultList.map {
                it.toPersistentSignedMemberInfo(context.holdingIdentity)
            }
        }

        return MemberInfoQueryResponse(
            persistentSignedMemberInfos.map { it.persistentMemberInfo },
            persistentSignedMemberInfos
        )
    }

    private fun MemberInfoEntity.toPersistentMemberInfo(viewOwningMember: net.corda.data.identity.HoldingIdentity) =
        memberInfoFactory.createPersistentMemberInfo(
            viewOwningMember,
            memberContext,
            mgmContext,
        )

    private fun MemberInfoEntity.toPersistentSignedMemberInfo(viewOwningMember: net.corda.data.identity.HoldingIdentity) =
        PersistentSignedMemberInfo(
            toPersistentMemberInfo(viewOwningMember),
            CryptoSignatureWithKey(
                ByteBuffer.wrap(memberSignatureKey),
                ByteBuffer.wrap(memberSignatureContent),
            ),
            CryptoSignatureSpec(memberSignatureSpec, null, null)
        )
}
