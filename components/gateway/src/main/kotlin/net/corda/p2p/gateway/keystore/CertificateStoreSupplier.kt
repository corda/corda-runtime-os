package net.corda.p2p.gateway.keystore

import java.io.IOException
import java.nio.file.Path

interface CertificateStoreSupplier {

    fun get(createNew: Boolean = false): CertificateStore

    fun getOptional(): CertificateStore? = try {
        get()
    } catch (e: IOException) {
        null
    }

    fun providerName(): String

    /** Artemis requires a path to an empty present file even if the keystore is not a file based one.*/
    val path: Path
}

class FileBasedCertificateStoreSupplier(override val path: Path, val storePassword: String, val entryPassword: String) : CertificateStoreSupplier {
    override fun get(createNew: Boolean) = CertificateStore.fromFile(path, storePassword, entryPassword, createNew)
    override fun providerName(): String = "JKS"
}
