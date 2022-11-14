package net.corda.utxo.token.sync

/**
 * Component factory used by the processor to create an instance of the [TokenCacheSyncComponentFactory]
 *
 * The component is structured in this way to avoid using OSGI for all constructing all internal services within
 * this component.
 */
interface TokenCacheSyncComponentFactory{
    fun create(): TokenCacheSyncServiceComponent
}

