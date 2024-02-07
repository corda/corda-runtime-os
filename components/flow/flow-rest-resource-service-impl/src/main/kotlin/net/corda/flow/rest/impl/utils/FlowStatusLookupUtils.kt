package net.corda.flow.rest.impl.utils

import net.corda.data.flow.FlowKey
import net.corda.v5.base.util.EncodingUtils
import net.corda.virtualnode.toCorda
import java.security.MessageDigest

/**
 * The primary key in the database has a maximum size of [255] chars.
 * Given that we're only using the key to uniquely identify each FlowStatus, a hash is sufficient for achieving that.
 * We concatenate an MD5 hash of the [FlowKey.id] with the holding identity short hash, making the odds of collision low.
 */
fun FlowKey.hash() : String {
    val idHash = EncodingUtils.toBase64(MessageDigest.getInstance("MD5").digest(this.id.toByteArray()))
    val holdingIdentityShortHash = this.identity.toCorda().shortHash
    return "$idHash$holdingIdentityShortHash"
}
