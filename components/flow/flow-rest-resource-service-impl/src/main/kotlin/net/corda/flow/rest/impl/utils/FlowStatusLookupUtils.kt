package net.corda.flow.rest.impl.utils

import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.data.flow.FlowKey
import net.corda.v5.base.util.EncodingUtils.toBase64
import net.corda.virtualnode.toCorda

/**
 * The primary key in the database has a maximum size of [255] chars.
 * Given that we're only using the key to uniquely identify each FlowStatus, a hash is sufficient for achieving that.
 * We concatenate a SHA-256 hash of the [FlowKey.id] with the holding identity short hash, resulting in an 88 character ID.
 */
fun FlowKey.hash() : String {
    val idHash = toBase64(this.id.toByteArray().sha256Bytes())
    val holdingIdentityShortHash = this.identity.toCorda().shortHash
    return "$idHash$holdingIdentityShortHash"
}
