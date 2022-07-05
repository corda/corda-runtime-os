package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.registration.ApproveRegistration
import net.corda.data.membership.command.registration.DeclineRegistration
import net.corda.data.membership.command.registration.ProcessMemberVerification
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.StartRegistration
import net.corda.data.membership.command.registration.VerifyMember
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.mgm.handler.ApproveRegistrationHandler
import net.corda.membership.impl.registration.dynamic.mgm.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.mgm.handler.RegistrationHandlerResult
import net.corda.membership.impl.registration.dynamic.mgm.handler.StartRegistrationHandler
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService

@Suppress("LongParameterList")
class RegistrationProcessor(
    clock: Clock,
    memberInfoFactory: MemberInfoFactory,
    membershipGroupReaderProvider: MembershipGroupReaderProvider,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    membershipPersistenceClient: MembershipPersistenceClient,
    membershipQueryClient: MembershipQueryClient,
    cryptoOpsClient: CryptoOpsClient,
    hashingService: DigestService,
    cipherSchemeMetadata: CipherSchemeMetadata,
) : StateAndEventProcessor<String, RegistrationState, RegistrationCommand> {

    override val keyClass = String::class.java
    override val stateValueClass = RegistrationState::class.java
    override val eventValueClass = RegistrationCommand::class.java

    companion object {
        val logger = contextLogger()
    }

    private val handlers = mapOf<Class<*>, RegistrationHandler<*>>(
        StartRegistration::class.java to StartRegistrationHandler(
            clock,
            memberInfoFactory,
            membershipGroupReaderProvider,
            membershipPersistenceClient,
            membershipQueryClient,
            cordaAvroSerializationFactory
        ),
        ApproveRegistration::class.java to ApproveRegistrationHandler(
            membershipPersistenceClient,
            membershipQueryClient,
            cipherSchemeMetadata,
            hashingService,
            clock,
            cryptoOpsClient,
            cordaAvroSerializationFactory,
        ),

    )

    override fun onNext(
        state: RegistrationState?,
        event: Record<String, RegistrationCommand>
    ): StateAndEventProcessor.Response<RegistrationState> {
        logger.info("Processing registration command.")
        val result = when (val command = event.value?.command) {
            is StartRegistration -> {
                logger.info("Received start registration command.")
                handlers[StartRegistration::class.java]?.invoke(event)
            }
            is VerifyMember -> {
                logger.info("Received verify member during registration command.")
                logger.warn("Unimplemented command.")
                null
            }
            is ProcessMemberVerification -> {
                logger.info("Received process member during registration command.")
                logger.warn("Unimplemented command.")
                null
            }
            is ApproveRegistration -> {
                logger.info("Received approve registration command.")
                handlers[ApproveRegistration::class.java]?.invoke(event)
            }
            is DeclineRegistration -> {
                logger.info("Received decline registration command.")
                logger.warn("Unimplemented command.")
                logger.warn("Declining registration because: ${command.reason}")
                null
            }
            else -> {
                logger.warn("Unhandled registration command received.")
                createEmptyResult(state)
            }
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