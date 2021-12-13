package net.corda.v5.application.flows

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * This annotation is required by any [Flow] that is designed to be initiated by a counterparty flow. The class must have at least a
 * constructor which takes in a single [net.corda.v5.application.identity.Party] parameter which represents the initiating counterparty. The
 * [Flow] that does the initiating is specified by the [value] property and itself must be annotated with [InitiatingFlow].
 *
 * The node on startup scans for [Flow]s which are annotated with this and automatically registers the initiating to initiated flow mapping.
 *
 * @see InitiatingFlow
 */
@Target(CLASS)
@MustBeDocumented
annotation class InitiatedBy(val value: KClass<out Flow<*>>)