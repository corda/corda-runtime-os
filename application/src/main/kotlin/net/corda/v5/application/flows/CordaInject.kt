package net.corda.v5.application.flows

import kotlin.annotation.AnnotationTarget.FIELD

/**
 * This annotation can be used with [Flow][net.corda.v5.application.flows.Flow] and
 * [CordaService][net.corda.v5.application.services.CordaService] to indicate which dependencies should be injected.
 */
@Target(FIELD)
@MustBeDocumented
annotation class CordaInject
