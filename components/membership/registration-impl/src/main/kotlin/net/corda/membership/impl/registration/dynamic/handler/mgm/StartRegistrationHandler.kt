package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.CREATION_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda

@Suppress("LongParameterList")
internal class StartRegistrationHandler(
    private val clock: Clock,
    private val memberInfoFactory: MemberInfoFactory,
    private val memberTypeChecker: MemberTypeChecker,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val membershipQueryClient: MembershipQueryClient,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : RegistrationHandler<StartRegistration> {

    private companion object {
        val logger = contextLogger()
    }

    private val keyValuePairListDeserializer =
        cordaAvroSerializationFactory.createAvroDeserializer({
            logger.error("Deserialization of registration request KeyValuePairList failed.")
        }, KeyValuePairList::class.java)

    override val commandType = StartRegistration::class.java

    override fun invoke(state: RegistrationState?, key: String, command: StartRegistration): RegistrationHandlerResult {
        val (registrationRequest, mgmHoldingId, pendingMemberHoldingId) =
            with(command) {
                Triple(
                    toRegistrationRequest(),
                    destination.toCorda(),
                    source.toCorda()
                )
            }

        val (outputCommand, outputStates) = try {
            validateRegistrationRequest(!memberTypeChecker.isMgm(pendingMemberHoldingId)) {
                "Registration request is registering an MGM holding identity."
            }
            val mgmMemberInfo = getMGMMemberInfo(mgmHoldingId)

            logger.info("Persisting the received registration request.")
            membershipPersistenceClient.persistRegistrationRequest(mgmHoldingId, registrationRequest).also {
                require(it as? MembershipPersistenceResult.Failure == null) {
                    "Failed to persist the received registration request. Reason: " +
                            (it as MembershipPersistenceResult.Failure).errorMsg
                }
            }

            logger.info("Registering $pendingMemberHoldingId with MGM for holding identity: $mgmHoldingId")
            val pendingMemberInfo = buildPendingMemberInfo(registrationRequest)
            // Parse the registration request and verify contents
            // The MemberX500Name matches the source MemberX500Name from the P2P messaging
            validateRegistrationRequest(
                pendingMemberInfo.name == pendingMemberHoldingId.x500Name
            ) { "MemberX500Name in registration request does not match member sending request over P2P." }

            // The MemberX500Name is not a duplicate
            val existingMemberInfo = membershipQueryClient.queryMemberInfo(
                mgmHoldingId,
                listOf(pendingMemberHoldingId)
            )
            validateRegistrationRequest(
                existingMemberInfo is MembershipQueryResult.Success
                        && (existingMemberInfo.payload.isEmpty()
                        || !existingMemberInfo.payload.sortedBy { it.modifiedTime }.last().isActive)
            ) { "The latest member info for given member is in 'Active' status" }

            // The group ID matches the group ID of the MGM
            validateRegistrationRequest(
                pendingMemberInfo.groupId == mgmMemberInfo.groupId
            ) { "Group ID in registration request does not match the group ID of the target MGM." }

            // There is at least one endpoint specified
            validateRegistrationRequest(
                pendingMemberInfo.endpoints.isNotEmpty()
            ) { "Registering member has not specified any endpoints" }

            // Validate role-specific information if any role is set
            validateRoleInformation(pendingMemberInfo)

            // Persist pending member info
            membershipPersistenceClient.persistMemberInfo(mgmHoldingId, listOf(pendingMemberInfo)).also {
                require(it as? MembershipPersistenceResult.Failure == null) {
                    "Failed to persist pending member info. Reason: " +
                            (it as MembershipPersistenceResult.Failure).errorMsg
                }
            }

            val persistentMemberInfo = PersistentMemberInfo.newBuilder()
                .setMemberContext(pendingMemberInfo.memberProvidedContext.toAvro())
                .setViewOwningMember(mgmMemberInfo.holdingIdentity.toAvro())
                .setMgmContext(pendingMemberInfo.mgmProvidedContext.toAvro())
                .build()
            val pendingMemberRecord = Record(
                topic = Schemas.Membership.MEMBER_LIST_TOPIC,
                key = "${mgmMemberInfo.holdingIdentity.shortHash}-${pendingMemberInfo.holdingIdentity.shortHash}",
                value = persistentMemberInfo,
            )

            logger.info("Successful initial validation of registration request with ID ${registrationRequest.registrationId}")
            Pair(VerifyMember(), listOf(pendingMemberRecord))
        } catch (ex: InvalidRegistrationRequestException) {
            logger.warn("Declined registration.", ex)
            Pair(DeclineRegistration(ex.originalMessage), emptyList())
        } catch (ex: Exception) {
            logger.warn("Declined registration.", ex)
            Pair(DeclineRegistration("Failed to verify registration request due to: [${ex.message}]"), emptyList())
        }



        return RegistrationHandlerResult(
            RegistrationState(
                registrationRequest.registrationId,
                pendingMemberHoldingId.toAvro(),
                mgmHoldingId.toAvro()
            ),
            listOf(
                Record(REGISTRATION_COMMAND_TOPIC, key, RegistrationCommand(outputCommand))
            ) + outputStates
        )
    }

    private class InvalidRegistrationRequestException(reason: String) : CordaRuntimeException(reason)

    private fun validateRegistrationRequest(condition: Boolean, errorMsg: () -> String) {
        if (!condition) {
            with(errorMsg.invoke()) {
                logger.error(this)
                throw InvalidRegistrationRequestException(this)
            }
        }
    }

    private fun buildPendingMemberInfo(registrationRequest: RegistrationRequest): MemberInfo {
        val memberContext = keyValuePairListDeserializer
            .deserialize(registrationRequest.memberContext.array())
            ?.items?.associate { it.key to it.value }?.toSortedMap()
            ?: emptyMap()
        validateRegistrationRequest(memberContext.entries.isNotEmpty()) {
            "Empty member context in the registration request."
        }

        val now = clock.instant().toString()
        return memberInfoFactory.create(
            memberContext.entries.associate { it.key to it.value }.toSortedMap(),
            sortedMapOf(
                CREATION_TIME to now,
                MODIFIED_TIME to now,
                STATUS to MEMBER_STATUS_PENDING
            )
        )
    }

    private fun getMGMMemberInfo(mgm: HoldingIdentity): MemberInfo {
        return memberTypeChecker.getMgmMemberInfo(mgm).apply {
            validateRegistrationRequest(this != null) {
                "Registration request is targeted at non-MGM holding identity."
            }
        }!!
    }

    private fun StartRegistration.toRegistrationRequest(): RegistrationRequest {
        return RegistrationRequest(
            RegistrationStatus.NEW,
            memberRegistrationRequest.registrationId,
            source.toCorda(),
            memberRegistrationRequest.memberContext,
            memberRegistrationRequest.memberSignature,
        )
    }

    private fun validateRoleInformation(member: MemberInfo) {
        // If role is set to notary, notary details are specified
        member.notaryDetails?.let { notary ->
            notary.servicePlugin?.let {
                validateRegistrationRequest(
                    it.isNotBlank()
                ) { "Registering member has specified an invalid notary service plugin type." }
            }
        }
    }
}