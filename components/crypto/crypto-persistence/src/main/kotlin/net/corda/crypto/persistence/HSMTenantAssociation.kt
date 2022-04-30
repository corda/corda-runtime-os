package net.corda.crypto.persistence

import net.corda.data.crypto.wire.hsm.HSMConfig

class HSMTenantAssociation(
    val tenantId: String,
    val category: String,
    val masterKeyAlias: String?,
    val aliasSecret: ByteArray?,
    val config: HSMConfig
)