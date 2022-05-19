package net.corda.crypto.persistence.db.model

object CryptoEntities {
    val classes = setOf(
        WrappingKeyEntity::class.java,
        SigningKeyEntity::class.java,
        HSMConfigEntity::class.java,
        HSMAssociationEntity::class.java,
        HSMCategoryAssociationEntity::class.java,
        HSMCategoryMapEntity::class.java
    )
}