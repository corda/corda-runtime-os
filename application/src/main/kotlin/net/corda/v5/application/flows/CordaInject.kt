package net.corda.v5.application.flows

import kotlin.annotation.AnnotationTarget.FIELD

/**
 * The [CordaInject] annotation should be used within [Flow]s to inject Corda's platform services.
 *
 * The annotation is placed onto a [Flow]'s fields/properties and not on constructor parameters.
 *
 * Example usage:
 *
 * - Kotlin:
 *
 * ```kotlin
 * class MyFlow : RestStartableFlow {
 *
 *     @CordaInject
 *     lateinit var flowEngine: FlowEngine
 *
 *     @CordaInject
 *     lateinit var flowMessaging: FlowMessaging
 *
 *     @Suspendable
 *     override fun call(requestBody: RestRequestBody): String {
 *         ...
 *     }
 * }
 * ```
 *
 * - Java:
 *
 * ```java
 * class MyFlow implements RestStartableFlow {
 *
 *     @CordaInject
 *     public FlowEngine flowEngine;
 *
 *     @CordaInject
 *     public FlowMessaging flowMessaging;
 *
 *     @Suspendable
 *     @Override
 *     public String call(RestRequestBody requestBody) {
 *         ...
 *     }
 * }
 * ```
 *
 * The properties annotated with [CordaInject] can be public or private as the reflection that sets them can bypass
 * their visibility modifiers. Making a [Flow]'s properties public, internal or package private is convenient for unit
 * testing and does not affect the [Flow]'s runtime behaviour.
 *
 * The injected properties cannot be accessed within a [Flow]'s constructor and should only be used from within a
 * [Flow]'s call method or subsequently executed code.
 */
@Target(FIELD)
@MustBeDocumented
annotation class CordaInject