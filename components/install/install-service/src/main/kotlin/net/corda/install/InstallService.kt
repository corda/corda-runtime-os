package net.corda.install

import net.corda.libs.packaging.CpkIdentifier
import net.corda.lifecycle.Lifecycle
import net.corda.packaging.CPK
import java.util.concurrent.CompletableFuture

interface InstallService : Lifecycle {
    fun get(id : CpkIdentifier): CompletableFuture<CPK?>
}