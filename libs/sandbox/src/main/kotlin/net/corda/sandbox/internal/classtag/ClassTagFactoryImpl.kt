package net.corda.sandbox.internal.classtag

import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.CLASS_TAG_DELIMITER
import net.corda.sandbox.internal.CLASS_TAG_IDENTIFIER_IDX
import net.corda.sandbox.internal.CLASS_TAG_VERSION_IDX
import net.corda.sandbox.internal.ClassTagV1
import net.corda.sandbox.internal.sandbox.CpkSandboxInternal
import net.corda.v5.crypto.SecureHash
import org.osgi.framework.Bundle
import java.util.NavigableSet
import java.util.TreeSet

/** An implementation of [ClassTagFactory]. */
internal class ClassTagFactoryImpl : ClassTagFactory {
    override fun createSerialised(
        isStaticClassTag: Boolean,
        isPlatformBundle: Boolean,
        bundle: Bundle,
        sandbox: Sandbox
    ): String {
        if (isPlatformBundle) {
            return if (isStaticClassTag) {
                StaticTagImplV1(isPlatformClass = true, bundle.symbolicName, ClassTagV1.PLACEHOLDER_CPK_FILE_HASH)
            } else {
                EvolvableTagImplV1(
                    isPlatformClass = true,
                    bundle.symbolicName,
                    ClassTagV1.PLACEHOLDER_CORDAPP_BUNDLE_NAME,
                    ClassTagV1.PLACEHOLDER_CPK_PUBLIC_KEY_HASHES
                )
            }.serialise()

        }

        if (sandbox !is CpkSandboxInternal) throw SandboxException("Sandbox was neither a platform sandbox nor a CPK " +
                "sandbox. A valid class tag cannot be constructed.")

        return if (isStaticClassTag) {
            StaticTagImplV1(isPlatformClass = false, bundle.symbolicName, sandbox.cpk.cpkHash)
        } else {
            EvolvableTagImplV1(
                isPlatformClass = false,
                bundle.symbolicName,
                sandbox.cordappBundle.symbolicName,
                sandbox.cpk.id.signers
            )
        }.serialise()
    }

    override fun deserialise(serialisedClassTag: String): ClassTag {
        val entries = serialisedClassTag.split(CLASS_TAG_DELIMITER)
        if (entries.size < 2) throw SandboxException(
            "Serialised class tag only contained ${entries.size} entries, whereas the minimum length is 2. The " +
                    "entries were $entries."
        )

        val type = entries[CLASS_TAG_IDENTIFIER_IDX]
        val versionString = entries[CLASS_TAG_VERSION_IDX]
        val version = versionString.toIntOrNull()
            ?: throw SandboxException(
                "Serialised class tag's version $versionString could not be converted to an integer."
            )

        return when {
            type == ClassTagV1.STATIC_IDENTIFIER && version == 1 -> StaticTagImplV1.deserialise(entries)
            type == ClassTagV1.EVOLVABLE_IDENTIFIER && version == 1 -> EvolvableTagImplV1.deserialise(entries)
            else -> throw SandboxException("Serialised class tag had type $type and version $version. This " +
                    "combination is unknown.")
        }
    }
}

/** Implements [StaticTag]. */
private class StaticTagImplV1(
    isPlatformClass: Boolean,
    classBundleName: String,
    cpkFileHash: SecureHash
) : StaticTag(1, isPlatformClass, classBundleName, cpkFileHash) {
    companion object {
        private const val ENTRIES_LENGTH = 5
        private const val IS_PLATFORM_CLASS_IDX = 2
        private const val CLASS_BUNDLE_NAME_IDX = 3
        private const val CPK_FILE_HASH_IDX = 4

        /** Deserialises a [StaticTagImplV1] class tag. */
        fun deserialise(classTagEntries: List<String>): StaticTagImplV1 {
            if (classTagEntries.size != ENTRIES_LENGTH) throw SandboxException(
                "Serialised static class tag contained ${classTagEntries.size} entries, whereas $ENTRIES_LENGTH " +
                        "entries were expected. The entries were $classTagEntries."
            )

            val isPlatformClass = classTagEntries[IS_PLATFORM_CLASS_IDX].toBoolean()

            val cpkFileHashString = classTagEntries[CPK_FILE_HASH_IDX]
            val cpkFileHash = try {
                SecureHash.create(cpkFileHashString)
            } catch (e: IllegalArgumentException) {
                throw SandboxException("Couldn't parse hash $cpkFileHashString in serialised static class tag.", e)
            }

            return StaticTagImplV1(isPlatformClass, classTagEntries[CLASS_BUNDLE_NAME_IDX], cpkFileHash)
        }
    }

    override fun serialise(): String {
        // This approach - of allocating an array of the expected length and adding the entries by index - is designed
        // to minimise errors where serialisation and deserialisation retrieve entries from different indices.
        val entries = arrayOfNulls<Any>(ENTRIES_LENGTH)

        entries[CLASS_TAG_IDENTIFIER_IDX] = ClassTagV1.STATIC_IDENTIFIER
        entries[CLASS_TAG_VERSION_IDX] = version
        entries[IS_PLATFORM_CLASS_IDX] = isPlatformClass
        entries[CLASS_BUNDLE_NAME_IDX] = classBundleName
        entries[CPK_FILE_HASH_IDX] = cpkFileHash

        return entries.joinToString(CLASS_TAG_DELIMITER)
    }
}

/** Implements [EvolvableTag]. */
private class EvolvableTagImplV1(
    isPlatformClass: Boolean,
    classBundleName: String,
    cordappBundleName: String,
    cpkPublicKeyHashes: NavigableSet<SecureHash>
) : EvolvableTag(1, isPlatformClass, classBundleName, cordappBundleName, cpkPublicKeyHashes) {

    companion object {
        private const val ENTRIES_LENGTH = 6
        private const val IS_PLATFORM_CLASS_IDX = 2
        private const val CLASS_BUNDLE_NAME_IDX = 3
        private const val CORDAPP_BUNDLE_NAME_IDX = 4
        private const val CPK_PUBLIC_KEY_HASHES_IDX = 5

        /** Deserialises an [EvolvableTagImplV1] class tag. */
        fun deserialise(classTagEntries: List<String>): EvolvableTagImplV1 {
            if (classTagEntries.size != ENTRIES_LENGTH) throw SandboxException(
                "Serialised evolvable class tag contained ${classTagEntries.size} entries, whereas $ENTRIES_LENGTH " +
                        "entries were expected. The entries were $classTagEntries."
            )

            val isPlatformClass = classTagEntries[IS_PLATFORM_CLASS_IDX].toBoolean()

            val cpkPublicKeyHashes = TreeSet(classTagEntries[CPK_PUBLIC_KEY_HASHES_IDX]
                .split(ClassTagV1.COLLECTION_DELIMITER)
                .filter { secureHash -> secureHash != "" } // Catches an edge-case when the list of signers is empty.
                .map { publicKeyHash -> try {
                    SecureHash.create(publicKeyHash)
                } catch  (e: IllegalArgumentException) {
                    throw SandboxException("Couldn't parse hash $publicKeyHash in serialised evolvable class tag.", e)
                } })

            return EvolvableTagImplV1(
                isPlatformClass,
                classTagEntries[CLASS_BUNDLE_NAME_IDX],
                classTagEntries[CORDAPP_BUNDLE_NAME_IDX],
                cpkPublicKeyHashes
            )
        }
    }

    override fun serialise(): String {
        val stringifiedCpkPublicKeyHashes = cpkPublicKeyHashes.joinToString(ClassTagV1.COLLECTION_DELIMITER)

        // This approach - of allocating an array of the expected length and adding the entries by index - is designed
        // to minimise errors where serialisation and deserialisation retrieve entries from different indices.
        val entries = arrayOfNulls<Any>(ENTRIES_LENGTH)

        entries[CLASS_TAG_IDENTIFIER_IDX] = ClassTagV1.EVOLVABLE_IDENTIFIER
        entries[CLASS_TAG_VERSION_IDX] = version
        entries[IS_PLATFORM_CLASS_IDX] = isPlatformClass
        entries[CLASS_BUNDLE_NAME_IDX] = classBundleName
        entries[CORDAPP_BUNDLE_NAME_IDX] = cordappBundleName
        entries[CPK_PUBLIC_KEY_HASHES_IDX] = stringifiedCpkPublicKeyHashes

        return entries.joinToString(CLASS_TAG_DELIMITER)
    }
}