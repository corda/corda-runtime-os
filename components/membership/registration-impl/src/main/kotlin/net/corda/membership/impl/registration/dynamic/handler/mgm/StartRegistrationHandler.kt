package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.impl.registration.dynamic.verifiers.RegistrationContextCustomFieldsVerifier
import net.corda.membership.lib.MemberInfoExtension.Companion.CREATION_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoExtension.Companion.preAuthToken
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SignedMemberInfo
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.p2p.helpers.P2pRecordsFactory
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
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class StartRegistrationHandler(
    private val clock: Clock,
    private val memberInfoFactory: MemberInfoFactory,
    private val memberTypeChecker: MemberTypeChecker,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val membershipQueryClient: MembershipQueryClient,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    ),
    private val registrationContextCustomFieldsVerifier: RegistrationContextCustomFieldsVerifier = RegistrationContextCustomFieldsVerifier()
) : RegistrationHandler<StartRegistration> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val keyValuePairListDeserializer =
        cordaAvroSerializationFactory.createAvroDeserializer({
            logger.error("Deserialization of registration request KeyValuePairList failed.")
        }, KeyValuePairList::class.java)

    override val commandType = StartRegistration::class.java

    override fun invoke(state: RegistrationState?, key: String, command: StartRegistration): RegistrationHandlerResult {
        if (state != null) {
            logger.info("Registration request ${command.memberRegistrationRequest.registrationId} had already started")
            return RegistrationHandlerResult(state, emptyList())
        }
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
            membershipPersistenceClient
                .persistRegistrationRequest(mgmHoldingId, registrationRequest)
                .getOrThrow()

            validateRegistrationRequest(registrationRequest.serial != null) {
                "Serial on the registration request should not be null."
            }

            logger.info("Registering $pendingMemberHoldingId with MGM for holding identity: $mgmHoldingId")
            val pendingMemberInfo = buildPendingMemberInfo(registrationRequest)

            validatePreAuthTokenUsage(mgmHoldingId, pendingMemberInfo)
            // Parse the registration request and verify contents
            // The MemberX500Name matches the source MemberX500Name from the P2P messaging
            validateRegistrationRequest(
                pendingMemberInfo.name == pendingMemberHoldingId.x500Name
            ) { "MemberX500Name in registration request does not match member sending request over P2P." }

            val existingMemberInfos = membershipQueryClient.queryMemberInfo(
                mgmHoldingId,
                listOf(pendingMemberHoldingId)
            ).getOrThrow()
            // The MemberX500Name is not a duplicate
            validateRegistrationRequest(
                existingMemberInfos.isEmpty() || registrationRequest.serial != 0L
            ) { "Member already exists with the same X500 name." }
            // Serial number on the request should be smaller than the current version of the requestor's MemberInfo
            val activeOrSuspendedInfo = existingMemberInfos.lastOrNull {
                it.status == MEMBER_STATUS_ACTIVE || it.status == MEMBER_STATUS_SUSPENDED
            }
            validateRegistrationRequest(
                activeOrSuspendedInfo == null || activeOrSuspendedInfo.serial <= registrationRequest.serial!!
            ) {
                "Registration request was submitted for an older version of member info. " +
                        "Please submit a new request."
            }

            // The group ID matches the group ID of the MGM
            validateRegistrationRequest(
                pendingMemberInfo.groupId == mgmMemberInfo.groupId
            ) { "Group ID in registration request does not match the group ID of the target MGM." }

            // There is at least one endpoint specified
            validateRegistrationRequest(
                pendingMemberInfo.endpoints.isNotEmpty()
            ) { "Registering member has not specified any endpoints" }

            // Validate role-specific information if any role is set
            validateRoleInformation(mgmHoldingId, pendingMemberInfo)

            val signedMemberInfo = SignedMemberInfo(
                pendingMemberInfo,
                registrationRequest.signature,
                registrationRequest.signatureSpec
            )

            // Persist pending member info
            membershipPersistenceClient.persistMemberInfo(mgmHoldingId, listOf(signedMemberInfo))
                .execute().also {
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
                key = "${mgmMemberInfo.holdingIdentity.shortHash}-${pendingMemberInfo.holdingIdentity.shortHash}" +
                        "-${pendingMemberInfo.status}",
                value = persistentMemberInfo,
            )
            val persistMemberStatusMessage = p2pRecordsFactory.createAuthenticatedMessageRecord(
                source = mgmHoldingId.toAvro(),
                destination = pendingMemberHoldingId.toAvro(),
                content = SetOwnRegistrationStatus(
                    registrationRequest.registrationId,
                    RegistrationStatus.RECEIVED_BY_MGM,
                ),
                minutesToWait = 5
            )

            logger.info("Successful initial validation of registration request with ID ${registrationRequest.registrationId}")
            Pair(VerifyMember(), listOf(pendingMemberRecord, persistMemberStatusMessage))
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
                logger.info(this)
                throw InvalidRegistrationRequestException(this)
            }
        }
    }

    private fun buildPendingMemberInfo(registrationRequest: RegistrationRequest): MemberInfo {
        val memberContext = keyValuePairListDeserializer
            .deserialize(registrationRequest.memberContext.array())
            ?.items?.associate { it.key to it.value }
            ?: emptyMap()
        validateRegistrationRequest(memberContext.isNotEmpty()) {
            "Empty member context in the registration request."
        }

        val customFieldsValid = registrationContextCustomFieldsVerifier.verify(memberContext)
        validateRegistrationRequest(customFieldsValid !is RegistrationContextCustomFieldsVerifier.Result.Failure) {
            (customFieldsValid as RegistrationContextCustomFieldsVerifier.Result.Failure).reason
        }

        val now = clock.instant().toString()
        return memberInfoFactory.create(
            memberContext.toSortedMap(),
            sortedMapOf(
                CREATION_TIME to now,
                MODIFIED_TIME to now,
                STATUS to MEMBER_STATUS_PENDING,
                SERIAL to (registrationRequest.serial!! + 1).toString(),
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
            RegistrationStatus.RECEIVED_BY_MGM,
            memberRegistrationRequest.registrationId,
            source.toCorda(),
            memberRegistrationRequest.memberContext,
            memberRegistrationRequest.memberSignature,
            memberRegistrationRequest.memberSignatureSpec,
            memberRegistrationRequest.serial,
        )
    }

    private fun validateRoleInformation(mgmHoldingId: HoldingIdentity, member: MemberInfo) {
        // If role is set to notary, notary details are specified
        member.notaryDetails?.let { notary ->
            validateRegistrationRequest(
                notary.keys.isNotEmpty()
            ) { "Registering member has role set to 'notary', but has missing notary key details." }
            notary.serviceProtocol?.let {
                validateRegistrationRequest(
                    it.isNotBlank()
                ) { "Registering member has specified an invalid notary service plugin type." }
            }
            // The notary service x500 name is different from the notary virtual node being registered.
            validateRegistrationRequest(
                member.name != notary.serviceName
            ) { "The virtual node `${member.name}` and the notary service `${notary.serviceName}`" +
                    " name cannot be the same." }
            // The notary service x500 name is different from any existing virtual node x500 name (notary or otherwise).
            validateRegistrationRequest(
                membershipQueryClient.queryMemberInfo(
                    mgmHoldingId,
                    listOf(HoldingIdentity(notary.serviceName, member.groupId))
                ).getOrThrow().firstOrNull() == null
            ) { "There is a virtual node having the same name as the notary service ${notary.serviceName}." }
            membershipGroupReaderProvider.getGroupReader(mgmHoldingId).groupParameters?.let { groupParameters ->
                validateRegistrationRequest(groupParameters.notaries.none { it.name == member.name }) {
                    "Registering member's name '${member.name}' is already in use as a notary service name."
                }
                validateRegistrationRequest(groupParameters.notaries.none { it.name == notary.serviceName }) {
                    "Notary service '${notary.serviceName}' already exists."
                }
            } ?: throw MembershipRegistrationException("Could not read group parameters of the membership group '${member.groupId}'.")
        }
    }

    /**
     * Fail to validate a registration request if a pre-auth token is present in the registration context, and
     * it is not a valid UUID or it is not currently an active token for the registering member.
     */
    private fun validatePreAuthTokenUsage(mgmHoldingId: HoldingIdentity, pendingMemberInfo: MemberInfo) {
        try {
            pendingMemberInfo.preAuthToken?.let {
                val result = membershipQueryClient.queryPreAuthTokens(
                    mgmHoldingIdentity = mgmHoldingId,
                    ownerX500Name = pendingMemberInfo.name,
                    preAuthTokenId = it,
                    viewInactive = false
                ).getOrThrow()
                validateRegistrationRequest(result.isNotEmpty()) {
                    logger.warn(
                        "'${pendingMemberInfo.name}' in group '${pendingMemberInfo.groupId}' attempted to " +
                                "register with invalid pre-auth token '$it'."
                    )
                    "Registration attempted to use a pre-auth token which is " +
                            "not currently active for this member."
                }
                result.first().ttl?.let {
                    validateRegistrationRequest(it >= clock.instant()) {
                        "Registration attempted to use a pre-auth token which has expired."
                    }
                }
                logger.info(
                    "'${pendingMemberInfo.name}' in group '${pendingMemberInfo.groupId}' has provided " +
                            "valid pre-auth token '$it' during registration."
                )
            }
        } catch (e: IllegalArgumentException) {
            with("Registration failed due to invalid format for the provided pre-auth token.") {
                logger.info(this, e)
                throw InvalidRegistrationRequestException(this)
            }
        } catch (e: MembershipQueryResult.QueryException) {
            with("Registration failed due to failure to query configured pre-auth tokens.") {
                logger.info(this, e)
                throw InvalidRegistrationRequestException(this)
            }
        }
    }
}