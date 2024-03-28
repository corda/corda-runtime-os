package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.db.schema.DbSchema
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.registration.RegistrationStatusExt.canMoveToStatus
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistRegistrationRequestHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistRegistrationRequest, Unit>(persistenceHandlerServices) {
    override val operation = PersistRegistrationRequest::class.java
    override fun invoke(context: MembershipRequestContext, request: PersistRegistrationRequest) {
        val registrationId = request.registrationRequest.registrationId
        // val id = UUID.randomUUID()
        // logger.info("QQQ 1 for $registrationId with $id requestId: ${context.requestId}")
        logger.info("Persisting registration request with ID [$registrationId] to status ${request.status}.")
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            // logger.info("QQQ 2 for $registrationId with $id")
            val currentRegistrationRequest = em.find(
                RegistrationRequestEntity::class.java,
                registrationId,
                LockModeType.PESSIMISTIC_WRITE,
            )
            if (currentRegistrationRequest != null) {
                getEntityToMerge(currentRegistrationRequest, request)?.also { toMerge ->
                    em.merge(toMerge)
                }
            } else {
                val now = clock.instant()
                /*
                val sql = """
                    INSERT INTO {h-schema}${DbSchema.VNODE_GROUP_REGISTRATION_TABLE}(
                        registration_id,
                        holding_identity_id,
                        status,
                        created,
                        last_modified,
                        member_context,
                        member_context_signature_key,
                        member_context_signature_content,
                        member_context_signature_spec,
                        registration_context,
                        registration_context_signature_key,
                        registration_context_signature_content,
                        registration_context_signature_spec,
                        serial,
                        reason)
                    VALUES (
                        :registration_id,
                        :holding_identity_id,
                        :status,
                        :created,
                        :last_modified,
                        :member_context,
                        :member_context_signature_key,
                        :member_context_signature_content,
                        :member_context_signature_spec,
                        :registration_context,
                        :registration_context_signature_key,
                        :registration_context_signature_content,
                        :registration_context_signature_spec,
                        :serial,
                        :reason)
                    ON CONFLICT(registration_id) DO UPDATE
                        SET 
                            status = EXCLUDED.status,
                            last_modified = EXCLUDED.last_modified,
                            serial = EXCLUDED.serial
                        """
                 */
                val sql = """
                    INSERT INTO {h-schema}${DbSchema.VNODE_GROUP_REGISTRATION_TABLE}(
                        registration_id,
                        holding_identity_id,
                        status,
                        created,
                        last_modified,
                        member_context,
                        member_context_signature_key,
                        member_context_signature_content,
                        member_context_signature_spec,
                        registration_context,
                        registration_context_signature_key,
                        registration_context_signature_content,
                        registration_context_signature_spec,
                        serial,
                        reason)
                    VALUES (
                        :REGISTRATION_ID,
                        :HOLDING_IDENTITY_ID,
                        :STATUS,
                        :CREATED,
                        :LAST_MODIFIED,
                        :MEMBER_CONTEXT,
                        :MEMBER_CONTEXT_SIGNATURE_KEY,
                        :MEMBER_CONTEXT_SIGNATURE_CONTENT,
                        :MEMBER_CONTEXT_SIGNATURE_SPEC,
                        :REGISTRATION_CONTEXT,
                        :REGISTRATION_CONTEXT_SIGNATURE_KEY,
                        :REGISTRATION_CONTEXT_SIGNATURE_CONTENT,
                        :REGISTRATION_CONTEXT_SIGNATURE_SPEC,
                        :SERIAL,
                        :REASON)
                        """
                em.createNativeQuery(sql)
                    .setParameter("REGISTRATION_ID", registrationId)
                    .setParameter("HOLDING_IDENTITY_ID", request.registeringHoldingIdentity.toCorda().shortHash.value)
                    .setParameter("STATUS", request.status.toString())
                    .setParameter("CREATED", now)
                    .setParameter("LAST_MODIFIED", now)
                    .setParameter("MEMBER_CONTEXT", request.registrationRequest.memberContext.data.array())
                    .setParameter(
                        "MEMBER_CONTEXT_SIGNATURE_KEY",
                        request.registrationRequest.memberContext.signature.publicKey.array()
                    )
                    .setParameter(
                        "MEMBER_CONTEXT_SIGNATURE_CONTENT",
                        request.registrationRequest.memberContext.signature.bytes.array()
                    )
                    .setParameter(
                        "MEMBER_CONTEXT_SIGNATURE_SPEC",
                        request.registrationRequest.memberContext.signatureSpec.signatureName
                    )
                    .setParameter(
                        "REGISTRATION_CONTEXT",
                        request.registrationRequest.registrationContext.data.array()
                    )
                    .setParameter(
                        "REGISTRATION_CONTEXT_SIGNATURE_KEY",
                        request.registrationRequest.registrationContext.signature.publicKey.array()
                    )
                    .setParameter(
                        "REGISTRATION_CONTEXT_SIGNATURE_CONTENT",
                        request.registrationRequest.registrationContext.signature.bytes.array()
                    )
                    .setParameter(
                        "REGISTRATION_CONTEXT_SIGNATURE_SPEC",
                        request.registrationRequest.registrationContext.signatureSpec.signatureName
                    )
                    .setParameter("SERIAL", request.registrationRequest.serial)
                    .setParameter("REASON", "")
                    .executeUpdate()
            }
        }
    }

    private fun getEntityToMerge(
        currentEntity: RegistrationRequestEntity,
        request: PersistRegistrationRequest,
    ): RegistrationRequestEntity? {
        val now = clock.instant()
        val registrationId = request.registrationRequest.registrationId
        val status = currentEntity.status.toStatus()
        return if (request.status == status) {
            // logger.info("QQQ 5 for $registrationId")
            logger.info(
                "Registration request [$registrationId] with status: $status" +
                    " is already persisted. Persistence request was discarded."
            )
            null
        } else if (!status.canMoveToStatus(request.status)) {
            // In case of processing persistence requests in an unordered manner we need to make sure the serial
            // gets persisted. All other existing data of the request will remain the same.
            if (request.status == RegistrationStatus.SENT_TO_MGM && currentEntity.serial == null) {
                // logger.info("QQQ 7 for $registrationId with $id")
                logger.info("Updating request [$registrationId] serial to ${request.registrationRequest.serial}")
                currentEntity.serial = request.registrationRequest.serial
                currentEntity.lastModified = now
                currentEntity
            } else {
                // logger.info("QQQ 6 for $registrationId with $id")
                logger.info(
                    "Registration request [$registrationId] has status: ${currentEntity.status}" +
                        " can not move it to status ${request.status}"
                )
                null
            }
        } else {
            currentEntity.status = request.status.toString()
            currentEntity.serial = request.registrationRequest.serial
            currentEntity.lastModified = now
            // logger.info("QQQ 9 for $registrationId with $id")
            currentEntity
        }
    }
}
