package net.corda.membership.db.lib

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentSignedMemberInfo
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.toMap
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.LockModeType

class PersistMemberInfoService(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val clock: Clock,
    private val memberInfoFactory: MemberInfoFactory,
) {
    private companion object {
        val logger = LoggerFactory.getLogger(PersistMemberInfoService::class.java)

        private fun Map<String, String>.removeTime(): Map<String, String> = this.filterKeys {
            it != MemberInfoExtension.CREATION_TIME && it != MemberInfoExtension.MODIFIED_TIME
        }
    }
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java,
        )
    }
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to deserialize key value pair list.")
        }
    }

    fun persist(
        em: EntityManager,
        members: Collection<PersistentSignedMemberInfo>,
    ) {
        logger.info("Persisting member information.")
        members.forEach {
            val memberInfo = memberInfoFactory.create(it.persistentMemberInfo)
            val currentMemberInfo = em.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(
                    memberInfo.groupId,
                    memberInfo.name.toString(),
                    memberInfo.status == MEMBER_STATUS_PENDING,
                ),
                LockModeType.PESSIMISTIC_WRITE,
            )
            if (currentMemberInfo?.serialNumber == memberInfo.serial) {
                val currentMemberContext = keyValuePairListDeserializer.deserializeKeyValuePairList(currentMemberInfo.memberContext)
                val currentMgmContext = keyValuePairListDeserializer.deserializeKeyValuePairList(currentMemberInfo.mgmContext)
                if (currentMemberContext.items != it.persistentMemberInfo.memberContext.items) {
                    throw MembershipPersistenceException(
                        "Cannot update member info with same serial number " +
                            "(${memberInfo.serial}): member context differs from original.",
                    )
                }
                if (currentMgmContext.toMap().removeTime() != it.persistentMemberInfo.mgmContext.toMap().removeTime()) {
                    throw MembershipPersistenceException(
                        "Cannot update member info with same serial number " +
                            "(${memberInfo.serial}): mgm context differs from original.",
                    )
                }
                return@forEach
            }

            val entity = MemberInfoEntity(
                memberInfo.groupId,
                memberInfo.name.toString(),
                memberInfo.status == MEMBER_STATUS_PENDING,
                memberInfo.status,
                clock.instant(),
                keyValuePairListSerializer.serializeKeyValuePairList(it.persistentMemberInfo.memberContext),
                it.memberSignature.publicKey.array(),
                it.memberSignature.bytes.array(),
                it.memberSignatureSpec.signatureName,
                keyValuePairListSerializer.serializeKeyValuePairList(it.persistentMemberInfo.mgmContext),
                memberInfo.serial,
            )
            em.merge(entity)
        }
    }
    private fun serializeContext(context: KeyValuePairList): ByteArray {
        return wrapWithNullErrorHandling({
            MembershipPersistenceException("Failed to serialize key value pair list.", it)
        }) {
            keyValuePairListSerializer.serialize(context)
        }
    }
}
