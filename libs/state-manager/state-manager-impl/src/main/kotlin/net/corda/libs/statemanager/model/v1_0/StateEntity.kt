package net.corda.libs.statemanager.model.v1_0

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import javax.persistence.Table
import javax.persistence.Version

@Entity
@Table(name = "state")
internal class StateEntity(
    // Getters and setters
    @Id
    @Column(name = "key", length = 255)
    val key: String,

    @Lob
    @Column(name = "state")
    val state: ByteArray,

    @Version
    @Column(name = "version")
    var version: Int? = null,

    @Column(name = "metadata", columnDefinition = "jsonb")
    var metadata: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StateEntity

        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }
}