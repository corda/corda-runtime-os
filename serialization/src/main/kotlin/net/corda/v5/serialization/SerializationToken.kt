package net.corda.v5.serialization

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.serialization.SingletonSerializationToken.Companion.singletonSerializationToken

/**
 * The interfaces and classes in this file allow large, singleton style classes to
 * mark themselves as needing converting to some form of token representation in the serialised form
 * and converting back again when deserializing.
 *
 * Typically these classes would be used for node services and subsystems that might become reachable from
 * Fibers and thus sucked into serialization when they are checkpointed.
 */

/**
 * This interface should be implemented by classes that want to substitute a token representation of themselves if
 * they are serialized because they have a lot of internal state that does not serialize (well).
 *
 * This models a similar pattern to the readReplace/writeReplace methods in Java serialization.
 */
@CordaSerializable
interface SerializeAsToken {
    fun toToken(context: SerializeAsTokenContext): SerializationToken
}

/**
 * This represents a token in the serialized stream for an instance of a type that implements [SerializeAsToken].
 */
interface SerializationToken {
    fun fromToken(context: SerializeAsTokenContext): Any
}

/**
 * A context for mapping SerializationTokens to/from SerializeAsTokens.
 */
interface SerializeAsTokenContext {
    fun withIdentifier(id: String, toBeTokenized: SerializeAsToken)
    fun fromIdentifier(id: String): SerializeAsToken
}

/**
 * A class representing a [SerializationToken] for some object that is not serializable but can be looked up
 * (when deserialized) via just the class name.
 */
class SingletonSerializationToken private constructor(val className: String) : SerializationToken {

    override fun fromToken(context: SerializeAsTokenContext) = context.fromIdentifier(className)

    fun registerWithContext(context: SerializeAsTokenContext, toBeTokenized: SerializeAsToken): SingletonSerializationToken {
        context.withIdentifier(className, toBeTokenized)
        return this
    }

    companion object {
        @JvmStatic
        fun singletonSerializationToken(toBeTokenized: Class<*>) = SingletonSerializationToken(toBeTokenized.name)
        @JvmStatic
        fun singletonSerializationToken(className: String) = SingletonSerializationToken(className)
    }
}

/**
 * This interface should be implemented by classes that want to substitute a singleton token representation of themselves if they are
 * serialized because they have a lot of internal state that does not serialize (well).
 *
 * This interface should only be used on singleton classes, meaning that only one should exist during runtime.
 *
 * @see SerializeAsToken
 * @see SingletonSerializationToken
 * @see SingletonSerializationToken.singletonSerializationToken
 */
interface SingletonSerializeAsToken: SerializeAsToken {

    /**
     * Gets the name of the singleton's token.
     *
     * Provide an implementation of this method to manually specify the name of this singleton's token. The token's name will be set to the
     * singleton's class name if the method is not overridden.
     *
     * @return The [String] name of the singleton's token.
     */
    val tokenName: String get() = javaClass.name

    /**
     * Creates the singleton's token and returns it.
     *
     * Prefer providing an override of [tokenName] to alter the [SingletonSerializationToken] output from this method. The [context]
     * should not be used here, as registration of the [SingletonSerializationToken] is done by Corda.
     *
     * @param context The [SerializeAsTokenContext] involved in registering the token.
     *
     * @return A [SingletonSerializationToken] representing the singleton's token.
     *
     * @see tokenName
     */
    override fun toToken(context: SerializeAsTokenContext): SingletonSerializationToken {
        return singletonSerializationToken(tokenName)
    }
}
