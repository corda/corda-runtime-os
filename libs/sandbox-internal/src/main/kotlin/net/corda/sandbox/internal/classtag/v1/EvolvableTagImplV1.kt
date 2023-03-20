package net.corda.sandbox.internal.classtag.v1

import net.corda.crypto.core.parseSecureHash
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.CLASS_TAG_DELIMITER
import net.corda.sandbox.internal.CLASS_TAG_IDENTIFIER_IDX
import net.corda.sandbox.internal.CLASS_TAG_VERSION_IDX
import net.corda.sandbox.internal.ClassTagV1
import net.corda.sandbox.internal.ClassTagV1.CLASS_BUNDLE_NAME_IDX
import net.corda.sandbox.internal.ClassTagV1.CLASS_TYPE_IDX
import net.corda.sandbox.internal.classtag.ClassType
import net.corda.sandbox.internal.classtag.EvolvableTag
import net.corda.v5.crypto.SecureHash

/** Implements [EvolvableTag]. */
internal data class EvolvableTagImplV1(
    override val classType: ClassType,
    override val classBundleName: String,
    override val mainBundleName: String,
    override val cpkSignerSummaryHash: SecureHash?
) : EvolvableTag() {
    override val version: Int = ClassTagV1.VERSION

    companion object {
        private const val ENTRIES_LENGTH = 6
        private const val MAIN_BUNDLE_NAME_IDX = 4
        private const val CPK_PUBLIC_KEY_HASHES_IDX = 5

        /** Deserialises an [EvolvableTagImplV1] class tag. */
        @Suppress("ThrowsCount")
        fun deserialise(classTagEntries: List<String>): EvolvableTagImplV1 {
            if (classTagEntries.size != ENTRIES_LENGTH) throw SandboxException(
                "Serialised evolvable class tag contained ${classTagEntries.size} entries, whereas $ENTRIES_LENGTH " +
                        "entries were expected. The entries were $classTagEntries."
            )

            val classType = classTypeFromString(classTagEntries[CLASS_TYPE_IDX])

            val cpkSignerSummaryHashString = classTagEntries[CPK_PUBLIC_KEY_HASHES_IDX]
            val cpkSignerSummaryHash = try {
                parseSecureHash(cpkSignerSummaryHashString)
            } catch (e: IllegalArgumentException) {
                throw SandboxException(
                    "Couldn't parse hash $cpkSignerSummaryHashString in serialised evolvable class tag.", e
                )
            }

            return EvolvableTagImplV1(
                classType,
                classTagEntries[CLASS_BUNDLE_NAME_IDX],
                classTagEntries[MAIN_BUNDLE_NAME_IDX],
                cpkSignerSummaryHash
            )
        }
    }

    override fun serialise(): String {
        // This approach - of allocating an array of the expected length and adding the entries by index - is designed
        // to minimise errors where serialisation and deserialisation retrieve entries from different indices.
        val entries = arrayOfNulls<Any>(ENTRIES_LENGTH)

        entries[CLASS_TAG_IDENTIFIER_IDX] = ClassTagV1.EVOLVABLE_IDENTIFIER
        entries[CLASS_TAG_VERSION_IDX] = version
        entries[CLASS_TYPE_IDX] = classTypeToString(classType)
        entries[CLASS_BUNDLE_NAME_IDX] = classBundleName
        entries[MAIN_BUNDLE_NAME_IDX] = mainBundleName
        entries[CPK_PUBLIC_KEY_HASHES_IDX] = cpkSignerSummaryHash

        return entries.joinToString(CLASS_TAG_DELIMITER)
    }
}