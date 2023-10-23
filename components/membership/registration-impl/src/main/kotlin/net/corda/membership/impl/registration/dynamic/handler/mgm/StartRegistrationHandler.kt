package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.impl.registration.verifiers.RegistrationContextCustomFieldsVerifier
import net.corda.membership.lib.ContextDeserializationException
import net.corda.membership.lib.MemberInfoExtension.Companion.CREATION_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.deserializeContext
import net.corda.membership.lib.registration.DECLINED_REASON_EMPTY_REGISTRATION_CONTEXT
import net.corda.membership.lib.registration.DECLINED_REASON_FOR_USER_GENERAL_INVALID_REASON
import net.corda.membership.lib.registration.DECLINED_REASON_FOR_USER_INTERNAL_ERROR
import net.corda.membership.lib.registration.DECLINED_REASON_GROUP_ID_IN_REQUEST_NOT_MATCHING_TARGET
import net.corda.membership.lib.registration.DECLINED_REASON_INVALID_NOTARY_SERVICE_PLUGIN_TYPE
import net.corda.membership.lib.registration.DECLINED_REASON_NAME_IN_REQUEST_NOT_MATCHING_NAME_IN_P2P_MSG
import net.corda.membership.lib.registration.DECLINED_REASON_NOTARY_MISSING_NOTARY_DETAILS
import net.corda.membership.lib.registration.DECLINED_REASON_NOTARY_LEDGER_KEY
import net.corda.membership.lib.registration.DECLINED_REASON_NOT_MGM_IDENTITY
import net.corda.membership.lib.registration.DECLINED_REASON_NO_ENDPOINTS_SPECIFIED
import net.corda.membership.lib.registration.DECLINED_REASON_RESISTRANT_IS_MGM
import net.corda.membership.lib.registration.DECLINED_REASON_SERIAL_NEGATIVE
import net.corda.membership.lib.registration.DECLINED_REASON_SERIAL_NULL
import net.corda.membership.lib.registration.RegistrationRequestHelpers.getPreAuthToken
import net.corda.membership.lib.toMap
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.apache.avro.specific.SpecificRecordBase
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.SortedMap

@Suppress("LongParameterList")
internal class StartRegistrationHandler(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val clock: Clock,
    private val memberInfoFactory: MemberInfoFactory,
    private val memberTypeChecker: MemberTypeChecker,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val membershipQueryClient: MembershipQueryClient,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val registrationContextCustomFieldsVerifier: RegistrationContextCustomFieldsVerifier = RegistrationContextCustomFieldsVerifier()
) : RegistrationHandler<StartRegistration> {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )

    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer { logger.error("Failed to serialize key value pair list.") }

    private fun serialize(context: KeyValuePairList): ByteArray {
        return keyValuePairListSerializer.serialize(context) ?: throw CordaRuntimeException(
            "Failed to serialize key value pair list."
        )
    }

    override val commandType = StartRegistration::class.java

    @Suppress("LongMethod")
    override fun invoke(state: RegistrationState?, key: String, command: StartRegistration): RegistrationHandlerResult {
        if (state == null) throw MissingRegistrationStateException
        val (registrationId, mgmHoldingId, pendingMemberHoldingId) = Triple(
            state.registrationId, state.mgm.toCorda(), state.registeringMember.toCorda()
        )

        val outputRecords: MutableList<Record<String, out SpecificRecordBase>> = mutableListOf()

        val outputCommand = try {
            val mgmMemberInfo = getMGMMemberInfo(mgmHoldingId)
            val registrationRequest = membershipQueryClient.queryRegistrationRequest(mgmHoldingId, registrationId)
                .getOrThrow()
            validateRegistrationRequest(registrationRequest != null,
                "Could not find registration request with ID `$registrationId`.", DECLINED_REASON_FOR_USER_INTERNAL_ERROR)

            logger.info("Registering $pendingMemberHoldingId with MGM for holding identity: $mgmHoldingId")
            val pendingMemberInfo = buildPendingMemberInfo(registrationRequest!!)
            // Parse the registration request and verify contents
            // The MemberX500Name matches the source MemberX500Name from the P2P messaging
            validateRegistrationRequest(pendingMemberInfo.name == pendingMemberHoldingId.x500Name,
                DECLINED_REASON_NAME_IN_REQUEST_NOT_MATCHING_NAME_IN_P2P_MSG, DECLINED_REASON_NAME_IN_REQUEST_NOT_MATCHING_NAME_IN_P2P_MSG)

            val persistentMemberInfo = memberInfoFactory.createPersistentMemberInfo(
                mgmMemberInfo.holdingIdentity.toAvro(),
                pendingMemberInfo,
            )
            val pendingMemberRecord = Record(
                topic = Schemas.Membership.MEMBER_LIST_TOPIC,
                key = "${mgmMemberInfo.holdingIdentity.shortHash}-${pendingMemberInfo.holdingIdentity.shortHash}" +
                        "-${pendingMemberInfo.status}",
                value = persistentMemberInfo,
            )
            // Persist pending member info so that we can notify the member of declined registration if failure occurs
            // after this point
            membershipPersistenceClient.persistMemberInfo(mgmHoldingId, listOf(pendingMemberInfo))
                .execute().also {
                    require(it as? MembershipPersistenceResult.Failure == null) {
                        "Failed to persist pending member info. Reason: " +
                                (it as MembershipPersistenceResult.Failure).errorMsg
                    }
                }
            outputRecords.add(pendingMemberRecord)

            logger.info("Updating the status of the registration request.")
            membershipPersistenceClient.setRegistrationRequestStatus(
                mgmHoldingId,
                registrationId,
                RegistrationStatus.STARTED_PROCESSING_BY_MGM
            ).execute().also {
                require(it as? MembershipPersistenceResult.Failure == null) {
                    "Failed to update the status of the registration request. Reason: " +
                            (it as MembershipPersistenceResult.Failure).errorMsg
                }
            }

            validateRegistrationRequest(registrationRequest.serial != null,
                DECLINED_REASON_SERIAL_NULL, DECLINED_REASON_SERIAL_NULL)
            validateRegistrationRequest(registrationRequest.serial!! >= 0,
                DECLINED_REASON_SERIAL_NEGATIVE, DECLINED_REASON_SERIAL_NEGATIVE)
            validateRegistrationRequest(!memberTypeChecker.isMgm(pendingMemberHoldingId),
                DECLINED_REASON_RESISTRANT_IS_MGM, DECLINED_REASON_RESISTRANT_IS_MGM)

            val activeOrSuspendedInfo = membershipQueryClient.queryMemberInfo(
                mgmHoldingId,
                listOf(pendingMemberHoldingId)
            ).getOrThrow().lastOrNull {
                it.status == MEMBER_STATUS_ACTIVE || it.status == MEMBER_STATUS_SUSPENDED
            }
            if (registrationRequest.serial!! > 0) { //re-registration
                val serialShouldBeZero =
                    "Member has not registered previously so serial number should be 0, but it was ${registrationRequest.serial}."
                validateRegistrationRequest(activeOrSuspendedInfo != null, serialShouldBeZero, serialShouldBeZero)

                val serialNotUpToDate =
                    "Registration request was submitted for an older version of member info. " +
                    "The submitted serial was ${registrationRequest.serial}, but the latest serial is ${activeOrSuspendedInfo!!.serial}. " +
                    "Please submit a new request with an up-to-date serial number."
                validateRegistrationRequest(activeOrSuspendedInfo.serial <= registrationRequest.serial!!,
                    serialNotUpToDate, serialNotUpToDate)
            } else if (registrationRequest.serial!! == 0L) { // initial registration
                validateRegistrationRequest(activeOrSuspendedInfo == null,
                    DECLINED_REASON_FOR_USER_GENERAL_INVALID_REASON, DECLINED_REASON_FOR_USER_GENERAL_INVALID_REASON)
            }

            validatePreAuthTokenUsage(mgmHoldingId, pendingMemberInfo, registrationRequest)

            activeOrSuspendedInfo?.let { previous ->
                val previousContext = previous.memberProvidedContext.toMap()
                val pendingContext = pendingMemberInfo.memberProvidedContext.toMap()
                val diff = ((pendingContext.entries - previousContext.entries) + (previousContext.entries - pendingContext.entries))
                    .filter {
                        it.key.startsWith(SESSION_KEYS) ||
                        it.key.startsWith(LEDGER_KEYS) ||
                        it.key.startsWith(ROLES_PREFIX) ||
                        it.key.startsWith("corda.notary")
                    }
                val diffInvalidMsgFn = { "Fields ${diff.map { it.key }} cannot be added, removed or updated during re-registration." }
                validateRegistrationRequest(diff.isEmpty(), diffInvalidMsgFn, diffInvalidMsgFn)
            }

            // The group ID matches the group ID of the MGM
            validateRegistrationRequest(pendingMemberInfo.groupId == mgmMemberInfo.groupId,
                DECLINED_REASON_GROUP_ID_IN_REQUEST_NOT_MATCHING_TARGET, DECLINED_REASON_GROUP_ID_IN_REQUEST_NOT_MATCHING_TARGET)

            // There is at least one endpoint specified
            validateRegistrationRequest(pendingMemberInfo.endpoints.isNotEmpty(),
                DECLINED_REASON_NO_ENDPOINTS_SPECIFIED, DECLINED_REASON_NO_ENDPOINTS_SPECIFIED)

            // Validate role-specific information if any role is set
            validateRoleInformation(mgmHoldingId, pendingMemberInfo)

            logger.info("Successful initial validation of registration request with ID ${registrationRequest.registrationId}")
            VerifyMember()
        } catch (ex: InvalidRegistrationRequestException) {
            logger.warn("Declined registration. ${ex.originalMessage}")
            DeclineRegistration(ex.originalMessage, ex.reasonForUser)
        } catch (ex: Exception) {
            logger.warn("Declined registration. ${ex.message}")
            DeclineRegistration("Failed to verify registration request due to: [${ex.message}]", DECLINED_REASON_FOR_USER_INTERNAL_ERROR)
        }
        outputRecords.add(Record(REGISTRATION_COMMAND_TOPIC, key, RegistrationCommand(outputCommand)))

        return RegistrationHandlerResult(
            state,
            outputRecords
        )
    }

    override fun getOwnerHoldingId(
        state: RegistrationState?,
        command: StartRegistration
    ): net.corda.data.identity.HoldingIdentity? = state?.registeringMember

    private class InvalidRegistrationRequestException(reason: String, val reasonForUser: String?) : CordaRuntimeException(reason)

    private fun validateRegistrationRequest(condition: Boolean, errorMsg: String, reasonForUser: String?) {
        if (!condition) {
            logger.info(errorMsg)
            throw InvalidRegistrationRequestException(errorMsg, reasonForUser)
        }
    }

    private fun validateRegistrationRequest(condition: Boolean, errorMsgFn: () -> String, reasonForUserFn: () -> String?) {
        if (!condition) {
            val errorMsg = errorMsgFn()
            val reasonForUser = reasonForUserFn()
            logger.info(errorMsg)
            throw InvalidRegistrationRequestException(errorMsg, reasonForUser)
        }
    }

    private fun buildPendingMemberInfo(registrationRequest: RegistrationRequestDetails): SelfSignedMemberInfo {
        val memberContext = registrationRequest.memberProvidedContext.data.array().deserializeContext(keyValuePairListDeserializer)
        validateRegistrationRequest(memberContext.isNotEmpty(),
            DECLINED_REASON_EMPTY_REGISTRATION_CONTEXT, DECLINED_REASON_EMPTY_REGISTRATION_CONTEXT)

        val customFieldsValid = registrationContextCustomFieldsVerifier.verify(memberContext)
        val errorMsgFn = { (customFieldsValid as RegistrationContextCustomFieldsVerifier.Result.Failure).reason }
        validateRegistrationRequest(customFieldsValid !is RegistrationContextCustomFieldsVerifier.Result.Failure, errorMsgFn, errorMsgFn)

        val now = clock.instant().toString()
        val mgmContext = sortedMapOf(
            CREATION_TIME to now,
            MODIFIED_TIME to now,
            STATUS to MEMBER_STATUS_PENDING,
            SERIAL to (registrationRequest.serial!! + 1).toString(),
        ).toKeyValuePairList()
        return memberInfoFactory.createSelfSignedMemberInfo(
            registrationRequest.memberProvidedContext.data.array(),
            serialize(mgmContext),
            registrationRequest.memberProvidedContext.signature,
            registrationRequest.memberProvidedContext.signatureSpec,
        )
    }

    private fun SortedMap<String, String>.toKeyValuePairList() =
        KeyValuePairList(entries.map { KeyValuePair(it.key, it.value) }.toList())

    private fun getMGMMemberInfo(mgm: HoldingIdentity): MemberInfo {
        return memberTypeChecker.getMgmMemberInfo(mgm).apply {
            validateRegistrationRequest(this != null, DECLINED_REASON_NOT_MGM_IDENTITY, DECLINED_REASON_NOT_MGM_IDENTITY)
        }!!
    }

    private fun validateRoleInformation(mgmHoldingId: HoldingIdentity, member: MemberInfo) {
        val groupReader = membershipGroupReaderProvider.getGroupReader(mgmHoldingId)
        val groupParameters = groupReader.groupParameters
            ?: throw MembershipRegistrationException("Could not read group parameters of the membership group '${member.groupId}'.")
        // If role is set to notary, notary details are specified
        member.notaryDetails?.let { notary ->
            validateRegistrationRequest(notary.keys.isNotEmpty(),
                DECLINED_REASON_NOTARY_MISSING_NOTARY_DETAILS, DECLINED_REASON_NOTARY_MISSING_NOTARY_DETAILS)

            notary.serviceProtocol?.let {
                validateRegistrationRequest(it.isNotBlank(),
                    DECLINED_REASON_INVALID_NOTARY_SERVICE_PLUGIN_TYPE, DECLINED_REASON_INVALID_NOTARY_SERVICE_PLUGIN_TYPE)
            }

            val differentNotaryServiceVnodeNameFn = { "The virtual node `${member.name}` and the notary service `${notary.serviceName}`" +
                    " name cannot be the same." }
            // The notary service x500 name is different from the notary virtual node being registered.
            validateRegistrationRequest(member.name != notary.serviceName,
                differentNotaryServiceVnodeNameFn, differentNotaryServiceVnodeNameFn)

            val serviceNameExistsForOtherVnodeFn =
                { "There is a virtual node having the same name as the notary service ${notary.serviceName}." }
            // The notary service x500 name is different from any existing virtual node x500 name (notary or otherwise).
            validateRegistrationRequest(
                membershipQueryClient.queryMemberInfo(
                    mgmHoldingId,
                    listOf(HoldingIdentity(notary.serviceName, member.groupId))
                ).getOrThrow().firstOrNull() == null, serviceNameExistsForOtherVnodeFn, { DECLINED_REASON_FOR_USER_GENERAL_INVALID_REASON })

            val notaryServiceExistsFn = { "Notary service '${notary.serviceName}' already exists." }
            validateRegistrationRequest(
                groupReader.lookup().none {
                    it.notaryDetails?.serviceName == notary.serviceName && it.name != member.name
                }, notaryServiceExistsFn, { DECLINED_REASON_FOR_USER_GENERAL_INVALID_REASON })

            if (member.ledgerKeys.isNotEmpty()) {
                  throw InvalidRegistrationRequestException(DECLINED_REASON_NOTARY_LEDGER_KEY, DECLINED_REASON_NOTARY_LEDGER_KEY)
            }
        }

        validateRegistrationRequest(groupParameters.notaries.none { it.name == member.name }, {
            "Registering member's name '${member.name}' is already in use as a notary service name."
        }, { DECLINED_REASON_FOR_USER_GENERAL_INVALID_REASON })
    }

    /**
     * Fail to validate a registration request if a pre-auth token is present in the registration context, and
     * it is not a valid UUID or it is not currently an active token for the registering member.
     */
    private fun validatePreAuthTokenUsage(
        mgmHoldingId: HoldingIdentity,
        pendingMemberInfo: MemberInfo,
        registrationRequest: RegistrationRequestDetails
    ) {
        try {
            registrationRequest.getPreAuthToken(keyValuePairListDeserializer)?.let {
                val result = membershipQueryClient.queryPreAuthTokens(
                    mgmHoldingIdentity = mgmHoldingId,
                    ownerX500Name = pendingMemberInfo.name,
                    preAuthTokenId = it,
                    viewInactive = false
                ).getOrThrow()
                validateRegistrationRequest(result.isNotEmpty(), {
                    logger.warn(
                        "'${pendingMemberInfo.name}' in group '${pendingMemberInfo.groupId}' attempted to " +
                                "register with invalid pre-auth token '$it'."
                    )
                    "Registration attempted to use a pre-auth token which is " +
                            "not currently active for this member."
                }, { DECLINED_REASON_FOR_USER_GENERAL_INVALID_REASON })
                result.first().ttl?.let {
                    validateRegistrationRequest(it >= clock.instant(),
                        "Registration attempted to use a pre-auth token which has expired.",
                        DECLINED_REASON_FOR_USER_GENERAL_INVALID_REASON)
                }
                logger.info(
                    "'${pendingMemberInfo.name}' in group '${pendingMemberInfo.groupId}' has provided " +
                            "valid pre-auth token '$it' during registration."
                )
            }
        } catch (e: IllegalArgumentException) {
            val reason = "Registration failed due to invalid format for the provided pre-auth token."
            e.mapToInvalidRegistrationRequestException(
                reason,
                reason
            )
        } catch (e: MembershipQueryResult.QueryException) {
            e.mapToInvalidRegistrationRequestException(
                "Registration failed due to failure to query configured pre-auth tokens.",
                DECLINED_REASON_FOR_USER_INTERNAL_ERROR
            )
        } catch (e: ContextDeserializationException) {
            e.mapToInvalidRegistrationRequestException(
                "Registration failed due to failure when deserializing registration context.",
                DECLINED_REASON_FOR_USER_INTERNAL_ERROR
            )
        }
    }

    private fun Exception.mapToInvalidRegistrationRequestException(message: String, reasonForUser: String?) {
        logger.info(message, this)
        throw InvalidRegistrationRequestException(message, reasonForUser)
    }
}