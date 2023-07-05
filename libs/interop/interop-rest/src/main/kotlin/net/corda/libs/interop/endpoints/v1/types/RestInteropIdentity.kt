package net.corda.libs.interop.endpoints.v1.types

import net.corda.crypto.core.ShortHash
import java.util.UUID

data class RestInteropIdentity(
    val x500Name: String,
    val groupId: UUID,
    val shortHash: ShortHash?
)
