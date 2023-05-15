package net.corda.membership.registration.management.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.PersistMemberRegistrationState
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.state.RegistrationState
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.db.lib.UpdateRegistrationRequestStatusService
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.management.impl.handler.MemberTypeChecker
import net.corda.membership.registration.management.impl.handler.MissingRegistrationStateException
import net.corda.membership.registration.management.impl.handler.RegistrationHandler
import net.corda.membership.registration.management.impl.handler.RegistrationHandlerResult
import net.corda.membership.registration.management.impl.handler.member.PersistMemberRegistrationStateHandler
import net.corda.membership.registration.management.impl.handler.member.ProcessMemberVerificationRequestHandler
import net.corda.membership.registration.management.impl.handler.mgm.ApproveRegistrationHandler
import net.corda.membership.registration.management.impl.handler.mgm.DeclineRegistrationHandler
import net.corda.membership.registration.management.impl.handler.mgm.ProcessMemberVerificationResponseHandler
import net.corda.membership.registration.management.impl.handler.mgm.StartRegistrationHandler
import net.corda.membership.registration.management.impl.handler.mgm.VerifyMemberHandler
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class RegistrationProcessor(
    clock: Clock,
    memberInfoFactory: MemberInfoFactory,
    membershipGroupReaderProvider: MembershipGroupReaderProvider,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    membershipConfig: SmartConfig,
    groupParametersWriterService: GroupParametersWriterService,
    transactionFactory: DbTransactionFactory,
    groupParametersFactory: GroupParametersFactory,
    keyEncodingService: KeyEncodingService,
) : StateAndEventProcessor<String, RegistrationState, RegistrationCommand> {

    override val keyClass = String::class.java
    override val stateValueClass = RegistrationState::class.java
    override val eventValueClass = RegistrationCommand::class.java

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val memberTypeChecker = MemberTypeChecker(membershipGroupReaderProvider)

    private val updateRegistrationRequestStatusService = UpdateRegistrationRequestStatusService(clock)

    private val handlers = mapOf<Class<*>, RegistrationHandler<*>>(
        StartRegistration::class.java to StartRegistrationHandler(
            clock,
            memberInfoFactory,
            memberTypeChecker,
            transactionFactory,
            membershipGroupReaderProvider,
            cordaAvroSerializationFactory,
        ),
        ApproveRegistration::class.java to ApproveRegistrationHandler(
            transactionFactory,
            clock,
            cordaAvroSerializationFactory,
            memberTypeChecker,
            membershipGroupReaderProvider,
            groupParametersFactory,
            groupParametersWriterService,
            memberInfoFactory,
            keyEncodingService,
        ),
        DeclineRegistration::class.java to DeclineRegistrationHandler(
            updateRegistrationRequestStatusService,
            clock,
            cordaAvroSerializationFactory,
            memberTypeChecker,
            membershipConfig,
        ),

        ProcessMemberVerificationRequest::class.java to ProcessMemberVerificationRequestHandler(
            clock,
            cordaAvroSerializationFactory,
            updateRegistrationRequestStatusService,
            memberTypeChecker,
        ),
        VerifyMember::class.java to VerifyMemberHandler(
            clock,
            cordaAvroSerializationFactory,
            updateRegistrationRequestStatusService,
            memberTypeChecker,
            membershipConfig,
        ),
        ProcessMemberVerificationResponse::class.java to ProcessMemberVerificationResponseHandler(
            updateRegistrationRequestStatusService,
            clock,
            cordaAvroSerializationFactory,
            memberTypeChecker,
            membershipConfig,
            transactionFactory,
            membershipGroupReaderProvider,
        ),
        PersistMemberRegistrationState::class.java to PersistMemberRegistrationStateHandler(
            updateRegistrationRequestStatusService,
        ),
    )

    @Suppress("ComplexMethod")
    override fun onNext(
        state: RegistrationState?,
        event: Record<String, RegistrationCommand>
    ): StateAndEventProcessor.Response<RegistrationState> {
        logger.info("Processing registration command for registration ID ${event.key}.")
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