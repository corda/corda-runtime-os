@file:JvmName("ContractsDSL")

package net.corda.v5.ledger.obsolete.contracts

import net.corda.v5.base.util.uncheckedCast
import java.security.PublicKey

// Modifying to test SNYK Delta - not to be merged

/**
 * Defines a simple domain specific language for the specification of financial contracts. Currently covers:
 *
 *  - Some utilities for working with commands.
 *  - A simple language extension for specifying requirements in English, along with logic to enforce them.
 */

//// Requirements /////////////////////////////////////////////////////////////////////////////////////////////////////

object Requirements {
    /** Throws [IllegalArgumentException] if the given expression evaluates to false. */
    @Suppress("NOTHING_TO_INLINE")   // Inlining this takes it out of our committed ABI.
    inline infix fun String.using(expr: Boolean) {
        if (!expr) throw IllegalArgumentException("Failed requirement: $this")
    }
}

inline fun <R> requireThat(body: Requirements.() -> R) = Requirements.body()

//// Authenticated commands ///////////////////////////////////////////////////////////////////////////////////////////

/** Filters the command list by type and public key all at once. */
inline fun <reified T : CommandData> Collection<Command<CommandData>>.select(signer: PublicKey? = null): List<Command<T>> {
    return select(T::class.java, signer)
}

/** Filters the command list by type and public key all at once. */
inline fun <reified T : CommandData> Collection<Command<CommandData>>.select(): List<Command<T>> {
    return select(T::class.java, signer = null)
}

/** Filters the command list by type and public key all at once. */
fun <C : CommandData> Collection<Command<CommandData>>.select(clazz: Class<C>, signer: PublicKey?): List<Command<C>> {
    return mapNotNull { if (clazz.isInstance(it.value)) uncheckedCast<Command<CommandData>, Command<C>>(it) else null }
        .filter { if (signer == null) true else signer in it.signers }
        .map { Command(it.value, it.signers) }
}

/** Filters the command list by type and public key all at once. */
fun <C : CommandData> Collection<Command<CommandData>>.select(clazz: Class<C>): List<Command<C>> {
    return select(clazz, signer = null)
}

/** Filters the command list by type, parties and public keys all at once. */
inline fun <reified T : CommandData> Collection<Command<CommandData>>.select(signers: Collection<PublicKey>?): List<Command<T>> {
    return select(T::class.java, signers)
}

/** Filters the command list by type and public keys all at once. */
fun <C : CommandData> Collection<Command<CommandData>>.select(clazz: Class<C>, signers: Collection<PublicKey>?): List<Command<C>> {
    return mapNotNull { if (clazz.isInstance(it.value)) uncheckedCast<Command<CommandData>, Command<C>>(it) else null }
        .filter { if (signers == null) true else it.signers.containsAll(signers) }
        .map { Command(it.value, it.signers) }
}

/** Ensures that a transaction has only one command that is of the given type, otherwise throws an exception. */
inline fun <reified T : CommandData> Collection<Command<CommandData>>.requireSingleCommand() = requireSingleCommand(T::class.java)

/** Ensures that a transaction has only one command that is of the given type, otherwise throws an exception. */
fun <C : CommandData> Collection<Command<CommandData>>.requireSingleCommand(clazz: Class<C>) = try {
    select(clazz).single()
} catch (e: NoSuchElementException) {
    throw IllegalStateException("Required ${clazz.kotlin.qualifiedName} command")   // Better error message.
}

/**
 * Simple functionality for verifying a move command. Verifies that each input has a signature from its owning key.
 *
 * @param T the type of the move command.
 *
 * @throws IllegalArgumentException
 */
inline fun <reified T : MoveCommand> verifyMoveCommand(
    inputs: List<OwnableState>,
    commands: List<Command<CommandData>>
): MoveCommand {
    // Now check the digital signatures on the move command. Every input has an owning public key, and we must
    // see a signature from each of those keys. The actual signatures have been verified against the transaction
    // data by the platform before execution.
    val owningPubKeys = inputs.map { it.owner.owningKey }.toSet()
    val command = commands.requireSingleCommand<T>()
    val keysThatSigned = command.signers.toSet()
    requireThat {
        "the owning keys are a subset of the signing keys" using keysThatSigned.containsAll(owningPubKeys)
    }
    return command.value
}
