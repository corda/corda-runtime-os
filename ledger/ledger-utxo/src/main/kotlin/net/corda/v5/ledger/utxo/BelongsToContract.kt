package net.corda.v5.ledger.utxo

import kotlin.reflect.KClass

/**
 * Indicates the [Contract] that the current state belongs to.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BelongsToContract(val value: KClass<out Contract>)
