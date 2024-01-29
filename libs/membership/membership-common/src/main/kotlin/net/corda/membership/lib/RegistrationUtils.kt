package net.corda.membership.lib

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.MemberContext

private const val NOTARY_KEY_ID = "corda.notary.keys.%s.id"
private val notaryIdRegex = NOTARY_KEY_ID.format("[0-9]+").toRegex()

/**
 * Transforms [KeyValuePairList] into map.
 */
fun KeyValuePairList.toMap() = items.associate { it.key to it.value }

/**
 * Transforms map into [KeyValuePairList].
 */
fun Map<String, String>.toWire(): KeyValuePairList {
    return KeyValuePairList(
        map {
            KeyValuePair(it.key, it.value)
        }
    )
}

/**
 * Transforms LayeredPropertyMap into [KeyValuePairList].
 */
fun LayeredPropertyMap.toWire(): KeyValuePairList {
    return KeyValuePairList(
        entries.map {
            KeyValuePair(it.key, it.value)
        }
    )
}

/**
 * Transforms [MemberContext] into map.
 */
fun MemberContext.toMap() = entries.associate { it.key to it.value }

/**
 * Verify back-chain flag movement for notaries during re-registration.
 */
fun verifyBackchainFlagMovement(previousContext: Map<String, String>, newContext: Map<String, String>) {
    // This property can only be null when upgrading from 5.0/5.1, and we should move it to `true`
    // because pre-5.2 notaries do not support optional backchain
    // Once the flag is set it should never change during re-registrations
    // (i.e. no true->false or false->true change allowed)
    val previousOptionalBackchainValue = previousContext[MemberInfoExtension.NOTARY_SERVICE_BACKCHAIN_REQUIRED]?.toBoolean()
    val currentOptionalBackchainValue = newContext[MemberInfoExtension.NOTARY_SERVICE_BACKCHAIN_REQUIRED]?.toBoolean()
    require(
        (previousOptionalBackchainValue == null && currentOptionalBackchainValue == true) ||
            previousOptionalBackchainValue == currentOptionalBackchainValue
    ) {
        "Optional back-chain flag can only move from 'none' to 'true' during re-registration."
    }
}

/**
 * Verify only certain data is modified during re-registration.
 * If there are not allowed changes in the new context, the re-registration attempt will be marked as invalid/declined.
 */
fun verifyReRegistrationChanges(
    previousRegistrationContext: Map<String, String>,
    newRegistrationContext: Map<String, String>,
): String? {
    verifyBackchainFlagMovement(previousRegistrationContext, newRegistrationContext)
    val newOrChangedKeys = newRegistrationContext.filter {
        previousRegistrationContext[it.key] != it.value
    }.keys
    val removed = previousRegistrationContext.keys.filter {
        !newRegistrationContext.keys.contains(it)
    }
    val changed = (newOrChangedKeys + removed).filter {
        it.startsWith(MemberInfoExtension.SESSION_KEYS) ||
            it.startsWith(MemberInfoExtension.LEDGER_KEYS) ||
            it.startsWith(MemberInfoExtension.ROLES_PREFIX) ||
            (it.startsWith("corda.notary") && !it.endsWith("service.backchain.required"))
    }.filter {
        // Just ignore the notary key ID all together. It was part of the context in 5.1 and was removed in 5.2
        !notaryIdRegex.matches(it)
    }
    if (changed.isNotEmpty()) {
        return "Fields $changed cannot be added, removed or updated during re-registration."
    }
    return null
}
