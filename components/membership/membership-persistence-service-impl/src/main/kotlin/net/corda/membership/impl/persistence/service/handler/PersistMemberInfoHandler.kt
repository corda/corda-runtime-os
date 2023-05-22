package net.corda.membership.impl.persistence.service.handler

import javax.persistence.LockModeType
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.toMap
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import net.corda.virtualnode.toCorda

internal class PersistMemberInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistMemberInfo, Unit>(persistenceHandlerServices) {
    override val operation = PersistMemberInfo::class.java
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer(
            { logger.error("Failed to deserialize key value pair list.") },
            KeyValuePairList::class.java
        )

    private fun serializeContext(context: KeyValuePairList): ByteArray {
        return wrapWithNullErrorHandling({
            MembershipPersistenceException("Failed to serialize key value pair list.", it)
        }) {
            keyValuePairListSerializer.serialize(context)
        }
    }

    private fun deserialize(data: ByteArray): KeyValuePairList {
        return keyValuePairListDeserializer.deserialize(data) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }


    override fun invoke(context: MembershipRequestContext, request: PersistMemberInfo) {
        if (request.members.isNotEmpty()) {
            logger.info("Persisting member information.")
            transaction(context.holdingIdentity.toCorda().shortHash) { em ->
                request.members.forEach {
                    val memberInfo = memberInfoFactory.create(it.persistentMemberInfo)
                    val currentMemberInfo = em.find(
                        MemberInfoEntity::class.java,
                        MemberInfoEntityPrimaryKey(
                            memberInfo.groupId,
                            memberInfo.name.toString(),
                            memberInfo.status == MEMBER_STATUS_PENDING
                        ),
                        LockModeType.PESSIMISTIC_WRITE
                    )
                    if (currentMemberInfo?.serialNumber == memberInfo.serial) {
                        val currentMemberContext = deserialize(currentMemberInfo.memberContext)
                        val currentMgmContext = deserialize(currentMemberInfo.mgmContext)
                        if (currentMemberContext.items != it.persistentMemberInfo.memberContext.items) {
                            throw MembershipPersistenceException(
                                "Cannot update member info with same serial number " +
                                        "(${memberInfo.serial}): member context differs from original."
                            )
                        }
                        if (currentMgmContext.toMap().removeTime() != it.persistentMemberInfo.mgmContext.toMap()
                                .removeTime()
                        ) {
                            throw MembershipPersistenceException(
                                "Cannot update member info with same serial number " +
                                        "(${memberInfo.serial}): mgm context differs from original."
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
                        serializeContext(it.persistentMemberInfo.memberContext),
                        it.memberSignature.publicKey.array(),
                        it.memberSignature.bytes.array(),
                        it.memberSignatureSpec.signatureName,
                        serializeContext(it.persistentMemberInfo.mgmContext),
                        memberInfo.serial,
                    )
                    em.merge(entity)
                }
            }
        }
    }

    private fun Map<String, String>.removeTime(): Map<String, String> = this.filterKeys {
        it != MemberInfoExtension.CREATION_TIME && it != MemberInfoExtension.MODIFIED_TIME
    }
}
