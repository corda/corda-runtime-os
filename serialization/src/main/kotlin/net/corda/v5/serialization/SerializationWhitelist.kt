package net.corda.v5.serialization


/**
 * Provide a subclass of this to whitelist types for
 * serialisation that you cannot otherwise annotate.
 */
interface SerializationWhitelist {
    /**
     * Optionally whitelist types for use in object serialization, as we lock down the types that can be serialized.
     *
     * For example, if you add a new [ContractState][net.corda.v5.ledger.contracts.ContractState] it needs to be whitelisted.  You can do that
     * either by adding the [CordaSerializable][net.corda.v5.base.annotations.CordaSerializable] annotation or via this method.
     */
    val whitelist: List<Class<*>>
}
