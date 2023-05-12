package net.corda.membership.db.lib

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.membership.datamodel.MemberInfoEntity
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager

class QueryMemberInfoService(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) {

    private companion object {
        val logger = LoggerFactory.getLogger(QueryMemberInfoService::class.java)
    }
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java,
        )
    }
    fun get(
        em: EntityManager,
        viewOwningMember: HoldingIdentity,
        members: Collection<HoldingIdentity>,
    ): Collection<PersistentMemberInfo> {
        val memberInfos = if (members.isEmpty()) {
            logger.info("Query filter list is empty. Returning full member list.")
            em.createQuery(
                "SELECT m FROM ${MemberInfoEntity::class.simpleName} m",
                MemberInfoEntity::class.java,
            ).resultList
        } else {
            logger.info("Querying for ${members.size} members MemberInfo(s).")
            members.flatMap { holdingIdentity ->
                em.createQuery(
                    "SELECT m FROM ${MemberInfoEntity::class.simpleName} " +
                        "m where m.groupId = :groupId and m.memberX500Name = :memberX500Name",
                    MemberInfoEntity::class.java,
                )
                    .setParameter("groupId", holdingIdentity.groupId)
                    .setParameter("memberX500Name", holdingIdentity.x500Name)
                    .resultList
            }
        }
        return memberInfos.map {
            PersistentMemberInfo(
                viewOwningMember,
                keyValuePairListDeserializer.deserialize(it.memberContext),
                keyValuePairListDeserializer.deserialize(it.mgmContext),
            )
        }
    }
}
