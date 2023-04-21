package net.corda.crypto.softhsm

fun interface SigningRepositoryFactory {
    fun getInstance(tenantId: String): SigningRepository
}