package com.r3.corda.testing.bundles.dogs

import net.corda.v5.base.annotations.CordaSerializable
import java.time.Instant
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

@CordaSerializable
@Entity
data class VersionedDog(
    @get:Id
    @get:Column
    var id: UUID,

    @Column
    var name: String,

    @Column
    var birthdate: Instant,

    @Column
    var owner: String?
) {
    constructor() : this(id = UUID.randomUUID(), name = "", birthdate = Instant.now(), owner = "")

    // The below doesn't make sense because it is per process only. It is only added in an attempt to
    // trigger an `entityManager.merge` to emit an UPDATE sql statement when no other state of the entity has changed
    // compared to its mapped DB state.
    @Column
    var version: Int = globalVersion++
}

var globalVersion = 0