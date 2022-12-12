package net.corda.v5.ledger.notary.plugin.api

/**
 * An annotation used on [PluggableNotaryClientFlowProvider] implementations to
 * define the type of the notary plugin it instantiates.
 * See [PluggableNotaryClientFlowProvider] for more details.
 *
 * This type is used to select the proper plugin when a transaction needs to be notarised.
 */
@kotlin.annotation.Target(AnnotationTarget.CLASS)
@kotlin.annotation.Retention
@MustBeDocumented
annotation class PluggableNotaryType(val type: String)
