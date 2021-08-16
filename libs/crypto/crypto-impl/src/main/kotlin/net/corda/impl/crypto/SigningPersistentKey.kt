package net.corda.impl.crypto

import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@Table(name = "crypto_signing_keys")
@Suppress("LongParameterList")
class SigningPersistentKey(
        @Column(name = "sandbox_id", length = 64, nullable = false)
        var sandboxId: String,

        @Id
        @Column(name = "public_key_hash", length = 130, nullable = false)
        var publicKeyHash: String,

        @Column(name = "external_id", nullable = true)
        var externalId: UUID?,

        @Lob
        @Column(name = "public_key", nullable = true)
        var publicKey: ByteArray,

        @Column(name = "alias", length = 64, nullable = true)
        var alias: String?,

        @Column(name = "master_key_alias", length = 64, nullable = true)
        var masterKeyAlias: String?,

        @Lob
        @Column(name = "private_key_material", nullable = true)
        var privateKeyMaterial: ByteArray?,

        @Column(name = "scheme_code_name", length = 130, nullable = false)
        var schemeCodeName: String,

        @Column(name = "version", nullable = false)
        var version: Int = 1
) : Cloneable {
        public override fun clone(): SigningPersistentKey = super.clone() as SigningPersistentKey
}