package net.corda.install

import net.corda.lifecycle.Lifecycle
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.v5.crypto.SecureHash
import java.util.NavigableSet
import java.util.concurrent.CompletableFuture

fun interface InstallServiceListener {
    /**
     * This method is invoked everytime the list of available [CPI]s changes
     * @param cpiIdentifier the updated list of available [CPI.Identifier]
     * @param delta a [NavigableSet] that contains the [CPI.Identifier] that either have been added
     * to the list of available
     */
    fun onUpdatedCPIList(cpiIdentifier: NavigableSet<CPI.Identifier>, delta: NavigableSet<CPI.Identifier>)
}

interface InstallService : Lifecycle {

    /**
     * Returns a CPI by id
     * @param id the [CPI.Metadata.id] of the [CPI] to lookup
     * @return a [CompletableFuture] that returns the requested [CPI] instance as soon as it's ready or null if
     * a [CPI] with the requested [CPI.Identifier] isn't available
     */
    fun get(id: CPI.Identifier): CompletableFuture<CPI?>

    /**
     * Returns a CPK by id
     * @param id the [CPK.Metadata.id] of the [CPK] to lookup
     * @return a [CompletableFuture] that returns the requested [CPK] instance as soon as it's ready or null if
     * a [CPK] with the requested [CPK.Identifier] isn't available
     */
    fun get(id : CPK.Identifier): CompletableFuture<CPK?>

    /**
     * Returns a CPK by hash
     * @param hash the [CPK.Metadata.hash] of the [CPK] to lookup
     * @return a [CompletableFuture] that returns the requested [CPK] instance as soon as it's ready or null if
     * a [CPK] with the requested [CPK.Metadata.hash] isn't available
     */
    fun getCPKByHash(hash : SecureHash): CompletableFuture<CPK?>

    /**
     * Lists all available [CPK]s
     * @return a [List] of [CPK.Metadata], each one for each available [CPK]s
     */
    fun listCPK() : List<CPK.Metadata>

    /**
     * Lists all available [CPI]s
     * @return a [List] of [CPI.Metadata], each one for each available [CPI]s
     */
    fun listCPI() : List<CPI.Metadata>

    /**
     * Registers an [InstallServiceListener] that will be invoked whenever the list of available [CPI]s changes
     * @return an opaque [AutoCloseable] instance that represents the registration,
     * calling close on the object will cause the listener to be de-registered
     * (which means it won't be invoked anymore and will be available for garbage collection)
     */
    fun registerForUpdates(installServiceListener: InstallServiceListener) : AutoCloseable
}