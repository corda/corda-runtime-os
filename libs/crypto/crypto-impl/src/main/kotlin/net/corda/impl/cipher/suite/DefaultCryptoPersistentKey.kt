package net.corda.impl.cipher.suite

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@Table(name = "crypto_basic_keys")
@Suppress("LongParameterList")
class DefaultCryptoPersistentKey(
        @Column(name = "sandbox_id", length = 64, nullable = false)
        var sandboxId: String,

        @Column(name = "partition", length = 64, nullable = false)
        var partition: String,

        @Id
        @Column(name = "alias", length = 130, nullable = false)
        // the length doesn't match the alias length in the SigningPersistentKey due that
        // here the alias can be concatenation of the partition and the actual alias
        var alias: String,

        @Lob
        @Column(name = "public_key", nullable = true)
        var publicKey: ByteArray? = null,

        @Lob
        @Column(name = "private_key", nullable = false)
        var privateKey: ByteArray = ByteArray(0),

        @Column(name = "algorithm_name", length = 130, nullable = false)
        var algorithmName: String,

        @Column(name = "version", nullable = false)
        var version: Int = 1
)