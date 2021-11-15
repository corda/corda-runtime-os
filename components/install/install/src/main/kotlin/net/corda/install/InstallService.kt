package net.corda.install

import net.corda.lifecycle.Lifecycle
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.v5.crypto.SecureHash
import java.util.NavigableSet
import java.util.concurrent.CompletableFuture

fun interface InstallServiceListener {
    fun onUpdatedCPIList(cpiIdentifier: NavigableSet<CPI.Identifier>, delta: NavigableSet<CPI.Identifier>)
}

interface InstallService : Lifecycle {

    fun get(id: CPI.Identifier): CompletableFuture<CPI?>

    fun get(id : CPK.Identifier): CompletableFuture<CPK?>

    fun getCPKByHash(hash : SecureHash): CompletableFuture<CPK?>

    fun listCPK() : List<CPK.Metadata>

    fun listCPI() : List<CPI.Metadata>

    fun registerForUpdates(installServiceListener: InstallServiceListener) : AutoCloseable
}