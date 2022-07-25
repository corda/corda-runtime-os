package net.corda.crypto.persistence.hsm

@Suppress("LongParameterList")
class HSMTenantAssociation(
    val id: String,
    val tenantId: String,
    val category: String,
    val masterKeyAlias: String?,
    val aliasSecret: ByteArray?,
    val hsmId: String,
    val deprecatedAt: Long
) {
    override fun toString(): String {
        return "id=$id(tenant=$tenantId,category=$category,hsmId=$hsmId,deprecated=$deprecatedAt)"
    }
}