package net.corda.p2p.linkmanager.common

import java.nio.ByteBuffer

internal data class GroupIdWithPublicKeyHash(
    val groupId: String,
    val hash: ByteBuffer
)
