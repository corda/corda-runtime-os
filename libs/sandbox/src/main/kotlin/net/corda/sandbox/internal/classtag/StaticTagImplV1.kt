package net.corda.sandbox.internal.classtag

import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.CLASS_TAG_CLASS_BUNDLE_NAME_IDX
import net.corda.sandbox.internal.CLASS_TAG_DELIMITER
import net.corda.sandbox.internal.CLASS_TAG_IDENTIFIER_IDX
import net.corda.sandbox.internal.CLASS_TAG_VERSION_IDX
import net.corda.sandbox.internal.ClassTagV1
import net.corda.sandbox.internal.CLASS_TAG_CLASS_TYPE_IDX
import net.corda.v5.crypto.SecureHash

/** Implements [StaticTag]. */
internal data class StaticTagImplV1(
    override val classType: ClassType,
    override val classBundleName: String,
    override val cpkFileHash: SecureHash
) : StaticTag() {
    override val version: Int = 1

    companion object {
        private const val ENTRIES_LENGTH = 5
        private const val CPK_FILE_HASH_IDX = 4

        /** Deserialises a [StaticTagImplV1] class tag. */
        fun deserialise(classTagEntries: List<String>): StaticTagImplV1 {
            if (classTagEntries.size != ENTRIES_LENGTH) throw SandboxException(
                "Serialised static class tag contained ${classTagEntries.size} entries, whereas $ENTRIES_LENGTH " +
                        "entries were expected. The entries were $classTagEntries."
            )

            val classTypeString = classTagEntries[CLASS_TAG_CLASS_TYPE_IDX]
            val classType = try {
                ClassType.valueOf(classTypeString)
            } catch (e: IllegalArgumentException) {
                throw SandboxException(
                    "Couldn't parse class type $classTypeString in serialised evolvable class tag.",
                    e
                )
            }

            val cpkFileHashString = classTagEntries[CPK_FILE_HASH_IDX]
            val cpkFileHash = try {
                SecureHash.create(cpkFileHashString)
            } catch (e: IllegalArgumentException) {
                throw SandboxException("Couldn't parse hash $cpkFileHashString in serialised static class tag.", e)
            }

            return StaticTagImplV1(classType, classTagEntries[CLASS_TAG_CLASS_BUNDLE_NAME_IDX], cpkFileHash)
        }
    }

    override fun serialise(): String {
        // This approach - of allocating an array of the expected length and adding the entries by index - is designed
        // to minimise errors where serialisation and deserialisation retrieve entries from different indices.
        val entries = arrayOfNulls<Any>(ENTRIES_LENGTH)

        entries[CLASS_TAG_IDENTIFIER_IDX] = ClassTagV1.STATIC_IDENTIFIER
        entries[CLASS_TAG_VERSION_IDX] = version
        entries[CLASS_TAG_CLASS_TYPE_IDX] = classType
        entries[CLASS_TAG_CLASS_BUNDLE_NAME_IDX] = classBundleName
        entries[CPK_FILE_HASH_IDX] = cpkFileHash

        return entries.joinToString(CLASS_TAG_DELIMITER)
    }
}