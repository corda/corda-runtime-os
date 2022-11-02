package net.corda.v5.ledger.notary.plugin.core

import net.corda.v5.base.annotations.CordaSerializable


/**
 * Representation of errors that can be returned by the notary (plugin). This is only a marker interface, the plugins
 * can define their own errors by implementing this interface. Please refer to the non-validating notary plugin for
 * a more detailed example.
 */
@CordaSerializable
interface NotaryError
