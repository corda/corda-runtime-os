package net.corda.flow.application.versioning.impl

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.versioning.VersionedReceiveFlowFactory
import net.corda.flow.application.versioning.impl.sessions.VersionReceivingFlowSession
import net.corda.flow.application.versioning.impl.sessions.VersionReceivingFlowSessionImpl
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.debug
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ReceiveVersioningFlow<T>(
    private val versionedFlowFactory: VersionedReceiveFlowFactory<T>,
    private val session: FlowSession
) : SubFlow<T> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(ReceiveVersioningFlow::class.java)
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
        val currentFlowVersioningInformation = versioningService.peekCurrentVersioning()

        val (versionedSession, agreedVersion) = if (currentFlowVersioningInformation != null) {
            if (log.isTraceEnabled) {
                log.trace(
                    "Using previously agreed platform version ${currentFlowVersioningInformation.first} to execute a version of " +
                            "${versionedFlowFactory.versionedInstanceOf.name}. Local platform version $localPlatformVersion."
                )
            }
            session to currentFlowVersioningInformation.first
        } else {
            require(session !is VersionReceivingFlowSession)
            if (log.isTraceEnabled) {
                log.trace(
                    "Requesting agreed platform version to determine what version of " +
                            "${versionedFlowFactory.versionedInstanceOf.name} to use. Local platform version $localPlatformVersion."
                )
            }

            val (agreedVersion, initialReceivedPayload) = session.receive(AgreedVersionAndPayload::class.java)

            if (agreedVersion.version > localPlatformVersion) {
                val message = "Received agreed platform version $agreedVersion to determine what version of " +
                        "${versionedFlowFactory.versionedInstanceOf.name} to use, but this version is greater than the local " +
                        "platform version $localPlatformVersion."
                log.warn(message)
                throw IllegalArgumentException(message)
            }

            if (log.isTraceEnabled) {
                log.trace(
                    "Received agreed platform version to determine what version of " +
                            "${versionedFlowFactory.versionedInstanceOf.name} to use. Local platform version " +
                            "$localPlatformVersion. Agreed platform version $agreedVersion."
                )
            }
            versioningService.setCurrentVersioning(agreedVersion.version)
            /*
            If the [initialReceivedPayload] is `null` then the peer session must have done a receive or close first, causing them to send
            the version information separately instead of leveraging the lazy combination of the first send. In this scenario, there is no
            need to create a [VersionReceivingFlowSession] because the behaviour of a normal [FlowSession] suffices.
            */
            if (initialReceivedPayload != null) {
                VersionReceivingFlowSessionImpl(
                    initialReceivedPayload,
                    session as FlowSessionInternal,
                    serializationService
                )
            } else {
                session
            } to agreedVersion.version
        }

        if (log.isTraceEnabled) {
            log.trace(
                "Determining what version of ${versionedFlowFactory.versionedInstanceOf.name} to use. Local " +
                        "platform version $localPlatformVersion. Agreed platform version $agreedVersion."
            )
        }
        val agreedVersionedFlow = try {
            versionedFlowFactory.create(agreedVersion, versionedSession)
        } catch (e: IllegalArgumentException) {
            log.warn(
                "Failed to determine flow version, there is no version $agreedVersion of " +
                        "${versionedFlowFactory.versionedInstanceOf.name} installed in the sandbox. Local platform version " +
                        "$localPlatformVersion."
            )
            throw IllegalArgumentException(
                "There is no version $agreedVersion of ${versionedFlowFactory.versionedInstanceOf.name} installed in the sandbox"
            )
        }
        log.debug {
            "Using ${agreedVersionedFlow::class.java.name} due to agreed version $agreedVersion"
        }
        return flowEngine.subFlow(agreedVersionedFlow)
    }
}
