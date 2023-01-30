package net.corda.internal.serialization.model

import net.corda.internal.serialization.amqp.Metadata
import net.corda.sandbox.SandboxGroup
import org.slf4j.LoggerFactory
import java.io.NotSerializableException
import java.lang.reflect.Type

/**
 * A [TypeLoader] obtains local types whose [TypeIdentifier]s will reflect those of remote types.
 */
interface TypeLoader {
    /**
     * Obtains local types which will have the same [TypeIdentifier]s as the remote types.
     *
     * @param remoteTypeInformation The type information for the remote types.
     */
    fun load(
            remoteTypeInformation: Collection<RemoteTypeInformation>,
            sandboxGroup: SandboxGroup,
            metadata: Metadata
    ): Map<TypeIdentifier, Type>
}

/**
 * A [TypeLoader] to build a class matching the supplied [RemoteTypeInformation] if none
 * is visible from the current classloader.
 */
class ClassTypeLoader: TypeLoader {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    val cache = DefaultCacheProvider.createCache<TypeIdentifier, Type>()

    override fun load(
            remoteTypeInformation: Collection<RemoteTypeInformation>,
            sandboxGroup: SandboxGroup,
            metadata: Metadata
    ): Map<TypeIdentifier, Type> {
        val remoteInformationByIdentifier = remoteTypeInformation.associateBy { it.typeIdentifier }

        // Grab all the types we can from the cache, or the classloader.
        val resolvedTypes = remoteInformationByIdentifier.asSequence().mapNotNull { (identifier, _) ->
            try {
                identifier to cache.computeIfAbsent(identifier) { identifier.getLocalType(sandboxGroup, metadata) }
            } catch (ex: ClassNotFoundException) {
                null
            }
        }.toMap()

        if (resolvedTypes.size != remoteTypeInformation.size) {
            val unresolvedTypes = remoteInformationByIdentifier.asSequence().mapNotNull { (identifier, information) ->
                if (identifier in resolvedTypes) null else information.prettyPrint()
            }.toSet().joinToString(",")

            throw NotSerializableException("Unable to resolve the following types: $unresolvedTypes")
        }

        return resolvedTypes
    }
}

