package net.corda.v5.application.flows

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Allows a [Flow] to be started by RPC.
 *
 * If the annotation is missing, the flow will not be allowed to start via RPC and an exception will be thrown if done so.
 */
@Target(CLASS)
@MustBeDocumented
annotation class StartableByRPC