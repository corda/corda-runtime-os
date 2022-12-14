package net.corda.simulator.runtime.utils

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.exceptions.NoProtocolAnnotationException
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SignatureSpecService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import java.lang.reflect.Field
import java.security.AccessController
import java.security.PrivilegedExceptionAction

/**
 * If a field of the given class is present, creates the given value and sets it on this flow.
 *
 * @param fieldClass The type of the field.
 * @param valueCreator A factory method to create the value to assign to the field.
 */
fun Flow.injectIfRequired(
    fieldClass: Class<*>,
    valueCreator: () -> Any
) {
    accessField(fieldClass)?.set(this, valueCreator())
}

fun Flow.accessField(fieldClass: Class<*>): Field? {
    val field = getSuperClassesFor(this.javaClass)
        .flatMap { it.declaredFields.toSet() }
        .firstOrNull { it.type.equals(fieldClass) && it.isAnnotationPresent(CordaInject::class.java) }
    AccessController.doPrivileged(PrivilegedExceptionAction {
        field?.isAccessible = true
    })
    return field
}

private fun getSuperClassesFor(clazz: Class<*>): List<Class<*>> {
    val superClasses = mutableListOf<Class<*>>()
    var target: Class<*>? = clazz
    while (target != null) {
        superClasses.add(target)
        target = target.superclass
    }
    return superClasses
}

/**
 * Converts this [MemberX500Name] to a unique name to use for the persistence sandbox for a member.
 */
val MemberX500Name.sandboxName: String
    get() {
        return this.toString()
            .replace("=", "_")
            .replace(" ", "_")
            .replace(",", "")
    }

/**
 * Checks the availability of an API.
 */
fun checkAPIAvailability(flow: Flow, configuration: SimulatorConfiguration){
    flow::class.java.declaredFields.forEach {
        if(!it.isAnnotationPresent(CordaInject::class.java))
            return

        if(it.type.name.startsWith("net.corda.v5")) {
            if (!availableAPIs.contains(it.type) && !configuration.serviceOverrides.containsKey(it.type)) {
                throw NotImplementedError(
                    "${it.type.name} is not implemented in Simulator for this release"
                )
            }
        }
        else
            throw NotImplementedError("Support for custom services is not implemented; service was ${it.type.name}")
    }
}

/**
 * Return the protocol of the flow
 */
fun Flow.getProtocol() : String =
    this.javaClass.getAnnotation(InitiatingFlow::class.java)?.protocol
        ?: this.javaClass.getAnnotation(InitiatedBy::class.java)?.protocol
        ?: throw NoProtocolAnnotationException(this.javaClass)


val availableAPIs = setOf(
    JsonMarshallingService::class.java,
    FlowEngine::class.java,
    FlowMessaging::class.java,
    MemberLookup::class.java,
    SigningService::class.java,
    DigitalSignatureVerificationService::class.java,
    PersistenceService::class.java,
    SignatureSpecService::class.java,
    SerializationService::class.java,
    ConsensualLedgerService::class.java
)
