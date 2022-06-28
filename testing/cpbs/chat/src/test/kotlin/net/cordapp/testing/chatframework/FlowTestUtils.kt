package net.cordapp.testing.chat

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import org.junit.jupiter.api.fail

/**
 * Validates the protocol declarations between a Flow and ResponderFlow match one another.
 */
fun validateProtocol(from: Flow, to: ResponderFlow)
{
    val annotationOfInitiating = from::class.java.getAnnotation(InitiatingFlow::class.java)
        ?: fail("InitiatingFlow ${from::class.java.name} has no @InitiatingFlow annotation")
    val annotationOfInitiatedBy = to::class.java.getAnnotation(InitiatedBy::class.java)
        ?: fail("InitiatedBy Flow ${to::class.java.name} has no @InitiatedBy annotation")

    if (annotationOfInitiating.protocol != annotationOfInitiatedBy.protocol) {
        fail ("Flow ${from::class.java.name} initiates protocol '${annotationOfInitiating.protocol}'" +
                " whilst ResponderFlow ${to::class.java.name} is initiated by protocol '${annotationOfInitiatedBy.protocol}'")
    }
}

/**
 * Helper to execute Flow call()s in parallel.
 * Executes every block concurrently. Will return once all blocks are complete.
 */
fun ExecuteConcurrently(vararg blocks: () -> Unit) {
    blocks.map { Thread { it() } }.onEach { it.start() }.onEach { it.join() }
}
