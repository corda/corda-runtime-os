package net.corda.membership.impl.registration.dynamic

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.PersistMemberRegistrationState
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.DistributeMembershipPackage
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.state.RegistrationState
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.impl.registration.dynamic.handler.member.PersistMemberRegistrationStateHandler
import net.corda.membership.impl.registration.dynamic.handler.member.ProcessMemberVerificationRequestHandler
import net.corda.membership.impl.registration.dynamic.handler.mgm.ApproveRegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.mgm.DeclineRegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.mgm.DistributeMembershipPackageHandler
import net.corda.membership.impl.registration.dynamic.handler.mgm.ProcessMemberVerificationResponseHandler
import net.corda.membership.impl.registration.dynamic.handler.mgm.StartRegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.mgm.VerifyMemberHandler
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger

@Suppress("LongParameterList")
class RegistrationProcessor(
    clock: Clock,
    memberInfoFactory: MemberInfoFactory,
    membershipGroupReaderProvider: MembershipGroupReaderProvider,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    membershipPersistenceClient: MembershipPersistenceClient,
    membershipQueryClient: MembershipQueryClient,
    cryptoOpsClient: CryptoOpsClient,
    cipherSchemeMetadata: CipherSchemeMetadata,
    merkleTreeProvider: MerkleTreeProvider,
    membershipConfig: SmartConfig,
    groupParametersWriterService: GroupParametersWriterService,
    groupParametersFactory: GroupParametersFactory,
) : StateAndEventProcessor<String, RegistrationState, RegistrationCommand> {

    override val keyClass = String::class.java
    override val stateValueClass = RegistrationState::class.java
    override val eventValueClass = RegistrationCommand::class.java

    companion object {
        val logger = contextLogger()
    }

    private val memberTypeChecker = MemberTypeChecker(membershipGroupReaderProvider)

    private val handlers = mapOf<Class<*>, RegistrationHandler<*>>(
        StartRegistration::class.java to StartRegistrationHandler(
            clock,
            memberInfoFactory,
            memberTypeChecker,
            membershipPersistenceClient,
            membershipQueryClient,
            cordaAvroSerializationFactory,
        ),
        ApproveRegistration::class.java to ApproveRegistrationHandler(
            membershipPersistenceClient,
            clock,
            cordaAvroSerializationFactory,
            memberTypeChecker,
            membershipGroupReaderProvider,
            groupParametersWriterService,
            groupParametersFactory,
        ),
        DistributeMembershipPackage::class.java to DistributeMembershipPackageHandler(
            membershipQueryClient,
            cipherSchemeMetadata,
            clock,
            cryptoOpsClient,
            cordaAvroSerializationFactory,
            merkleTreeProvider,
            membershipConfig,
            membershipGroupReaderProvider,
        ),
        DeclineRegistration::class.java to DeclineRegistrationHandler(
            membershipPersistenceClient,
            clock,
            cordaAvroSerializationFactory,
            memberTypeChecker,
            membershipConfig,
        ),

        ProcessMemberVerificationRequest::class.java to ProcessMemberVerificationRequestHandler(
            clock,
            cordaAvroSerializationFactory,
            membershipPersistenceClient,
            memberTypeChecker,
        ),
        VerifyMember::class.java to VerifyMemberHandler(
            clock,
            cordaAvroSerializationFactory,
            membershipPersistenceClient,
            memberTypeChecker,
            membershipConfig,
        ),
        ProcessMemberVerificationResponse::class.java to ProcessMemberVerificationResponseHandler(
            membershipPersistenceClient,
            clock,
            cordaAvroSerializationFactory,
            memberTypeChecker,
            membershipConfig,
            membershipQueryClient,
            membershipGroupReaderProvider,
        ),
        PersistMemberRegistrationState::class.java to PersistMemberRegistrationStateHandler(
            membershipPersistenceClient,
        ),
    )

    @Suppress("ComplexMethod")
    override fun onNext(
        state: RegistrationState?,
        event: Record<String, RegistrationCommand>
    ): StateAndEventProcessor.Response<RegistrationState> {
        logger.info("Processing registration command.")
        val result = try {
            when (val command = event.value?.command) {
                is StartRegistration -> {
                    logger.info("Received start registration command.")
                    handlers[StartRegistration::class.java]?.invoke(state, event)
                }
                is VerifyMember -> {
                    logger.info("Received verify member during registration command.")
                    handlers[VerifyMember::class.java]?.invoke(state, event)
                }
                is ProcessMemberVerificationResponse -> {
                    logger.info("Received process member verification response during registration command.")
                    handlers[ProcessMemberVerificationResponse::class.java]?.invoke(state, event)
                }
                is ApproveRegistration -> {
                    logger.info("Received approve registration command.")
                    handlers[ApproveRegistration::class.java]?.invoke(state, event)
                }
                is DistributeMembershipPackage -> {
                    logger.info("Received distribute membership package command.")
                    handlers[DistributeMembershipPackage::class.java]?.invoke(state, event)
                }
                is DeclineRegistration -> {
                    logger.info("Received decline registration command.")
                    logger.warn("Declining registration because: ${command.reason}")
                    handlers[DeclineRegistration::class.java]?.invoke(state, event)
                }
                is ProcessMemberVerificationRequest -> {
                    logger.info("Received process member verification request during registration command.")
                    handlers[ProcessMemberVerificationRequest::class.java]?.invoke(state, event)
                }
                is PersistMemberRegistrationState -> {
                    logger.info("Received persist member registration state command.")
                    handlers[PersistMemberRegistrationState::class.java]?.invoke(state, event)
                }
                else -> {
                    logger.warn("Unhandled registration command received.")
                    createEmptyResult(state)
                }
            }
        } catch (e: MissingRegistrationStateException) {
            logger.error("RegistrationState was null during dynamic registration.", e)
            createEmptyResult()
        } catch (e: Exception) {
            logger.error("Unexpected error in handling registration command.", e)
            createEmptyResult()
        }
        return StateAndEventProcessor.Response(
            result?.updatedState,
            result?.outputStates ?: emptyList()
        )
    }

    private fun createEmptyResult(state: RegistrationState? = null): RegistrationHandlerResult {
        return RegistrationHandlerResult(state, emptyList())
    }
}
