package net.corda.p2p.linkmanager

import java.nio.ByteBuffer

internal data class GroupIdWithPublicKeyHash(
    val groupId: String,
    val hash: ByteBuffer
)
