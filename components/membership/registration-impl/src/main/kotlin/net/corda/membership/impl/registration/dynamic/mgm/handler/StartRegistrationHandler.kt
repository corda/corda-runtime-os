package net.corda.membership.impl.registration.dynamic.mgm.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.DeclineRegistration
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.StartRegistration
import net.corda.data.membership.command.registration.VerifyMember
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.MemberInfoExtension.Companion.CREATION_TIME
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.impl.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.impl.MemberInfoExtension.Companion.STATUS
import net.corda.membership.impl.MemberInfoExtension.Companion.endpoints
import net.corda.membership.impl.MemberInfoExtension.Companion.groupId
import net.corda.membership.impl.MemberInfoExtension.Companion.isMgm
import net.corda.membership.impl.toSortedMap
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda

@Suppress("LongParameterList")
class StartRegistrationHandler(
    private val clock: Clock,
    private val memberInfoFactory: MemberInfoFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val membershipQueryClient: MembershipQueryClient,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : RegistrationHandler {

    private companion object {
        val logger = contextLogger()
    }

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer({
            logger.error("Deserialization of registration request KeyValuePairList failed.")
        }, KeyValuePairList::class.java)

    override fun invoke(command: Record<String, RegistrationCommand>): RegistrationHandlerResult {
        val (registrationRequest, mgmHoldingId, pendingMemberHoldingId) =
            with(command.value!!.command as? StartRegistration) {
                require(this != null) {
                    "Incorrect handler used for command of type ${command.value!!.command::class.java}"
                }
                Triple(
                    toRegistrationRequest(),
                    destination.toCorda(),
                    source.toCorda()
                )
            }

        val outputCommand = RegistrationCommand(
            try {
                logger.info("Persisting the received registration request.")
                membershipPersistenceClient.persistRegistrationRequest(mgmHoldingId, registrationRequest).also {
                    require(it.errorMsg == null) {
                        "Failed to persist the received registration request. Reason: ${it.errorMsg}"
                    }
                }

                val mgmMemberInfo = getMGMMemberInfo(mgmHoldingId)
                logger.info("Registering with MGM for holding identity: $mgmHoldingId")
                val pendingMemberInfo = buildPendingMemberInfo(registrationRequest)
                // Parse the registration request and verify contents
                // The MemberX500Name matches the source MemberX500Name from the P2P messaging
                validateRegistrationRequest(
                    pendingMemberInfo.name == MemberX500Name.parse(pendingMemberHoldingId.x500Name)
                ) { "MemberX500Name in registration request does not match member sending request over P2P." }

                // The MemberX500Name is not a duplicate
                validateRegistrationRequest(
                    membershipQueryClient.queryMemberInfo(
                        mgmHoldingId,
                        listOf(pendingMemberHoldingId)
                    ).payload.isNullOrEmpty()
                ) { "Member Info already exists for applying member" }

                // The group ID matches the group ID of the MGM
                validateRegistrationRequest(
                    pendingMemberInfo.groupId == mgmMemberInfo.groupId
                ) { "Group ID in registration request does not match the group ID of the target MGM." }

                // There is at least one endpoint specified
                validateRegistrationRequest(
                    pendingMemberInfo.endpoints.isNotEmpty()
                ) { "Registering member has not specified any endpoints" }

                // Persist pending member info
                membershipPersistenceClient.persistMemberInfo(mgmHoldingId, pendingMemberInfo).also {
                    require(it.errorMsg == null) {
                        "Failed to persist pending member info. Reason: ${it.errorMsg}"
                    }
                }

                logger.info("Successful initial validation of registration request with ID ${registrationRequest.registrationId}")
                VerifyMember()
            } catch (ex: InvalidRegistrationRequestException) {
                DeclineRegistration(ex.originalMessage)
            } catch (ex: Exception) {
                DeclineRegistration("Failed to verify registration request due to: [${ex.message}]")
            }
        )

        return RegistrationHandlerResult(
            RegistrationState(registrationRequest.registrationId, pendingMemberHoldingId.toAvro()),
            listOf(Record(command.topic, command.key, outputCommand))
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
            ?.toSortedMap()
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
        val mgmMemberName = MemberX500Name.parse(mgm.x500Name)
        return membershipGroupReaderProvider.getGroupReader(mgm).lookup(mgmMemberName).apply {
            validateRegistrationRequest(this != null) {
                "Could not find MGM matching name: [$mgmMemberName]"
            }
            validateRegistrationRequest(this!!.isMgm) {
                "Registration request is targeted at non-MGM holding identity."
            }
        }!!
    }

    private fun StartRegistration.toRegistrationRequest(): RegistrationRequest = RegistrationRequest(
        memberRegistrationRequest.registrationId,
        source.toCorda(),
        memberRegistrationRequest.memberContext,
        memberRegistrationRequest.memberSignature.publicKey,
        memberRegistrationRequest.memberSignature.bytes
    )
}