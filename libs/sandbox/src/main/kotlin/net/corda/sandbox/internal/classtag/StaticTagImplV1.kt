package net.corda.sandbox.internal.classtag

import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.CLASS_TAG_DELIMITER
import net.corda.sandbox.internal.CLASS_TAG_IDENTIFIER_IDX
import net.corda.sandbox.internal.CLASS_TAG_VERSION_IDX
import net.corda.sandbox.internal.ClassTagV1
import net.corda.v5.crypto.SecureHash

/** Implements [StaticTag]. */
internal class StaticTagImplV1(
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