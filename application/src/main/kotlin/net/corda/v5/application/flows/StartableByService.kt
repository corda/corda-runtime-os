package net.corda.v5.application.flows

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Any [Flow] which is to be started by the [FlowStarterService] interface from within a [CordaService] must have this annotation. If it's
 * missing the flow will not be allowed to start and an exception will be thrown.
 */
@Target(CLASS)
@MustBeDocumented
annotation class StartableByService