package net.corda.crypto.persistence.hsm

class HSMTenantAssociation(
    val id: String,
    val tenantId: String,
    val category: String,
    val masterKeyAlias: String?,
    val aliasSecret: ByteArray?,
    val config: HSMConfig
)