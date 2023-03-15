package net.corda.flow.application.versioning.impl

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.flow.application.versioning.impl.sessions.VersionSendingFlowSessionImpl
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class VersioningFlow<T>(
    private val versionedFlowFactory: VersionedSendFlowFactory<T>,
    private val sessions: List<FlowSession>
) : SubFlow<T> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(VersioningFlow::class.java)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var versioningService: VersioningService

    @Suspendable
    override fun call(): T {
        val localPlatformVersion = memberLookup.myInfo().platformVersion
        val counterpartyPlatformVersions = counterpartyPlatformVersions()

        val currentFlowVersioningInformation = versioningService.peekCurrentVersioning()

        val (agreedVersion, versionedSessions) = if (currentFlowVersioningInformation != null) {
            log.trace {
                "Using previously agreed platform version ${currentFlowVersioningInformation.first} to execute a version of " +
                        "${versionedFlowFactory.versionedInstanceOf.name}. Local platform version $localPlatformVersion."
            }
            currentFlowVersioningInformation.first to sessions
        } else {
            log.trace {
                "Determining what version of ${versionedFlowFactory.versionedInstanceOf.name} to use. Local " +
                        "platform version $localPlatformVersion. Peer platform versions " +
                        "${counterpartyPlatformVersions.mapKeys { (name, _) -> name }}"
            }

            val agreedVersion = findCommonVersion(localPlatformVersion, counterpartyPlatformVersions)

            versioningService.setCurrentVersioning(agreedVersion)

            val versionedSessions = sessions.map { session ->
                require(session !is VersionSendingFlowSessionImpl)
                VersionSendingFlowSessionImpl(agreedVersion, linkedMapOf(), session as FlowSessionInternal, serializationService)
            }

            agreedVersion to versionedSessions
        }

        val agreedVersionedFlow = try {
            versionedFlowFactory.create(agreedVersion, versionedSessions)
        } catch (e: IllegalArgumentException) {
            // May want a specific exception that the factory can throw which is caught here.
            log.warn(
                "Failed to determine flow version, there is no version $agreedVersion of " +
                        "${versionedFlowFactory.versionedInstanceOf.name} installed in the sandbox. Local platform version " +
                        "$localPlatformVersion. Peer platform versions " +
                        "${counterpartyPlatformVersions.mapKeys { (name, _) -> name }}"
            )
            throw IllegalArgumentException(
                "There is no version $agreedVersion of ${versionedFlowFactory.versionedInstanceOf.name} installed in the sandbox"
            )
        }
        when {
            log.isDebugEnabled -> log.debug(
                "Using ${agreedVersionedFlow::class.java.name} due to agreed platform version $agreedVersion."
            )
            log.isTraceEnabled -> log.trace(
                "Using ${agreedVersionedFlow::class.java.name} due to agreed platform version $agreedVersion. " +
                        "Local platform version $localPlatformVersion. Peer platform versions " +
                        "${counterpartyPlatformVersions.mapKeys { (name, _) -> name }}."
            )
        }
        return flowEngine.subFlow(agreedVersionedFlow)
    }

    private fun counterpartyPlatformVersions(): Map<MemberX500Name, Int> {
        return sessions.map { session ->
            requireNotNull(memberLookup.lookup(session.counterparty)) {
                "Member ${session.counterparty} does not exist within the network"
            }
        }.associate { info -> info.name to info.platformVersion }
    }

    private fun findCommonVersion(localPlatformVersion: Int, counterpartyPlatformVersions: Map<MemberX500Name, Int>): Int {
        return (counterpartyPlatformVersions.map { (_, version) -> version } + localPlatformVersion).min()
    }
}