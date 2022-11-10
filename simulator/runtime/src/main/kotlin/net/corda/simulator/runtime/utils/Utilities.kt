package net.corda.simulator.runtime.utils

import net.corda.simulator.exceptions.NonImplementedAPIException
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name

/**
 * If a field of the given class is present, creates the given value and sets it on this flow.
 *
 * @param fieldClass The type of the field.
 * @param valueCreator A factory method to create the value to assign to the field.
 */
fun <T> Flow.injectIfRequired(
    fieldClass: Class<T>,
    valueCreator: () -> T
) = this.javaClass.declaredFields.firstOrNull {
    it.type.equals(fieldClass) && it.canAccess(this) && it.isAnnotationPresent(CordaInject::class.java)
}?.set(this, valueCreator())

/**
 * Converts this [MemberX500Name] to a unique name to use for the persistence sandbox for a member.
 */
val MemberX500Name.sandboxName: Any
    get() {
        return this.toString()
            .replace("=", "_")
            .replace(" ", "_")
            .replace(",", "")
    }

fun checkAPIAvailability(flow: Flow){
    flow::class.java.declaredFields.forEach {
        if(!it.isAnnotationPresent(CordaInject::class.java))
            return

        if(it.type.name.startsWith("net.corda.v5")) {
            if (!availableAPIs.contains(it.type)) {
                throw NonImplementedAPIException(it.type.name)
            }
        }
        else
            throw NonImplementedAPIException("Support for Custom Services")
    }
}

private val availableAPIs = listOf(
    JsonMarshallingService::class.java,
    FlowEngine::class.java,
    FlowMessaging::class.java,
    MemberLookup::class.java,
    SigningService::class.java,
    DigitalSignatureVerificationService::class.java,
    PersistenceService::class.java
)