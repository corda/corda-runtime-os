package net.corda.flow.application.versioning.impl

import net.corda.v5.base.annotations.CordaSerializable

/**
 * [AgreedVersion] is sent to peers during the versioning protocol to specify the agreed flow version to use. Also contains additional
 * context in a [Map] to keep the protocol flexible in the future.
 *
 * @property version The agreed platform version to use.
 * @property additionalContext Additional context that may be needed when versioning flows in the future.
 */
@CordaSerializable
data class AgreedVersion(val version: Int, val additionalContext: LinkedHashMap<String, Any>)