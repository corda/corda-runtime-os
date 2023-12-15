package net.corda.virtualnode.read.rest.extensions

import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.ShortHash.Companion.LENGTH
import net.corda.crypto.core.ShortHashException
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService

/**
 * Returns the virtual node info by short-hash code, for a given holding identity without starting any bundles or
 * instantiating any classes.
 *
 * Implementation may return null if it is part of a component.
 *
 * @param holdingIdentityShortHash The [ShortHash] of the virtual node to get.
 *
 * @return The [VirtualNodeInfo] matching [holdingIdentityShortHash], or throw a [ResourceNotFoundException] if it does
 * not exist.
 *
 * @throws ResourceNotFoundException If no virtual node matches [holdingIdentityShortHash].
 *
 * @see VirtualNodeInfoReadService.getByHoldingIdentityShortHash
 */
fun VirtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(holdingIdentityShortHash: ShortHash): VirtualNodeInfo {
    return getByHoldingIdentityShortHash(holdingIdentityShortHash)
        ?: throw ResourceNotFoundException("Virtual Node", holdingIdentityShortHash.value)
}

/**
 * Returns the virtual node info by short-hash code, for a given holding identity without starting any bundles or
 * instantiating any classes.
 *
 * Implementation may return null if it is part of a component.
 *
 * @param holdingIdentityShortHash The [ShortHash] of the virtual node to get.
 * @param message The message to include in the [ResourceNotFoundException] if the virtual node does not exist.
 *
 * @return The [VirtualNodeInfo] matching [holdingIdentityShortHash], or throw a [ResourceNotFoundException] if it does
 * not exist.
 *
 * @throws ResourceNotFoundException If no virtual node matches [holdingIdentityShortHash].
 *
 * @see VirtualNodeInfoReadService.getByHoldingIdentityShortHash
 */
fun VirtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(
    holdingIdentityShortHash: ShortHash,
    message: () -> String
): VirtualNodeInfo {
    return getByHoldingIdentityShortHash(holdingIdentityShortHash) ?: throw ResourceNotFoundException(message())
}

/**
 * Returns the virtual node info by short-hash code, for a given holding identity without starting any bundles or
 * instantiating any classes.
 *
 * Implementation may return null if it is part of a component.
 *
 * @param holdingIdentityShortHash The string representation of a [ShortHash] of the virtual node to get.
 *
 * @return The [VirtualNodeInfo] matching [holdingIdentityShortHash], or throw a [ResourceNotFoundException] if it does
 * not exist.
 *
 * @throws BadRequestException If the [holdingIdentityShortHash] does not parse into a valid [ShortHash].
 * @throws ResourceNotFoundException If no virtual node matches [holdingIdentityShortHash].
 *
 * @see VirtualNodeInfoReadService.getByHoldingIdentityShortHash
 */
fun VirtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(holdingIdentityShortHash: String): VirtualNodeInfo {
    val shortHash = ShortHash.parseOrThrow(holdingIdentityShortHash)
    return getByHoldingIdentityShortHash(shortHash) ?: throw ResourceNotFoundException("Virtual Node", shortHash.value)
}

/**
 * Returns the virtual node info by short-hash code, for a given holding identity without starting any bundles or
 * instantiating any classes.
 *
 * Implementation may return null if it is part of a component.
 *
 * @param holdingIdentityShortHash The string representation of a [ShortHash] of the virtual node to get.
 * @param message The message to include in the [ResourceNotFoundException] if the virtual node does not exist.
 *
 * @return The [VirtualNodeInfo] matching [holdingIdentityShortHash], or throw a [ResourceNotFoundException] if it does
 * not exist.
 *
 * @throws BadRequestException If the [holdingIdentityShortHash] does not parse into a valid [ShortHash].
 * @throws ResourceNotFoundException If no virtual node matches [holdingIdentityShortHash].
 *
 * @see VirtualNodeInfoReadService.getByHoldingIdentityShortHash
 */
fun VirtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(
    holdingIdentityShortHash: String,
    message: () -> String
): VirtualNodeInfo {
    val shortHash = ShortHash.parseOrThrow(holdingIdentityShortHash)
    return getByHoldingIdentityShortHash(shortHash) ?: throw ResourceNotFoundException(message())
}

/**
 * Creates a short hash from the given [holdingIdentityShortHash].
 *
 * For consistency with [SecureHash.toHexString], any lower case alpha characters are converted to uppercase.
 *
 * @throws [BadRequestException] If the string is not hexadecimal, or shorter than [LENGTH].
 *
 * @see ShortHash.of
 */
@SuppressWarnings("SwallowedException")
fun ShortHash.Companion.ofOrThrow(holdingIdentityShortHash: String): ShortHash {
    return try {
        of(holdingIdentityShortHash)
    } catch (e: ShortHashException) {
        throw BadRequestException("Invalid holding identity short hash${e.message?.let { ": $it" }}")
    }
}

/**
 * Creates a short hash parsing the given [holdingIdentityShortHash].
 *
 * For consistency with [SecureHash.toHexString], any lower case alpha characters are converted to uppercase.
 *
 * @throws [BadRequestException] If the string is not hexadecimal or has length different from [LENGTH].
 *
 * @see ShortHash.of
 */
@SuppressWarnings("SwallowedException")
fun ShortHash.Companion.parseOrThrow(holdingIdentityShortHash: String): ShortHash {
    return try {
        parse(holdingIdentityShortHash)
    } catch (e: ShortHashException) {
        throw BadRequestException("Invalid holding identity short hash${e.message?.let { ": $it" }}")
    }
}

/**
 * Attempts to create a key id [ShortHash] from specified hex string. Converts thrown [ShortHashException] if
 * [ShortHash.of] fails to http manageable [BadRequestException].
 */
fun createKeyIdOrHttpThrow(keyIdHexString: String): ShortHash {
    try {
        return ShortHash.of(keyIdHexString)
    } catch (e: ShortHashException) {
        throw BadRequestException("Invalid key id hex string: ${e.message}")
    }
}
