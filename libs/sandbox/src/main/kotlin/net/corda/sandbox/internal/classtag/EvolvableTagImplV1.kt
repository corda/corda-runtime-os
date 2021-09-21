package net.corda.sandbox.internal.classtag

import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.CLASS_TAG_DELIMITER
import net.corda.sandbox.internal.CLASS_TAG_IDENTIFIER_IDX
import net.corda.sandbox.internal.CLASS_TAG_VERSION_IDX
import net.corda.sandbox.internal.ClassTagV1
import net.corda.v5.crypto.SecureHash
import java.util.NavigableSet
import java.util.TreeSet

/** Implements [EvolvableTag]. */
internal class EvolvableTagImplV1(
    isNonCpkClass: Boolean,
    classBundleName: String,
    cordappBundleName: String,
    cpkPublicKeyHashes: NavigableSet<SecureHash>
) : EvolvableTag(1, isNonCpkClass, classBundleName, cordappBundleName, cpkPublicKeyHashes) {

    companion object {
        private const val ENTRIES_LENGTH = 6
        private const val IS_NON_CPK_CLASS_IDX = 2
        private const val CLASS_BUNDLE_NAME_IDX = 3
        private const val CORDAPP_BUNDLE_NAME_IDX = 4
        private const val CPK_PUBLIC_KEY_HASHES_IDX = 5

        /** Deserialises an [EvolvableTagImplV1] class tag. */
        fun deserialise(classTagEntries: List<String>): EvolvableTagImplV1 {
            if (classTagEntries.size != ENTRIES_LENGTH) throw SandboxException(
                "Serialised evolvable class tag contained ${classTagEntries.size} entries, whereas $ENTRIES_LENGTH " +
                        "entries were expected. The entries were $classTagEntries."
            )

            val isNonCpkClass = classTagEntries[IS_NON_CPK_CLASS_IDX].toBoolean()

            val cpkPublicKeyHashes = TreeSet(classTagEntries[CPK_PUBLIC_KEY_HASHES_IDX]
                .split(ClassTagV1.COLLECTION_DELIMITER)
                .filter { secureHash -> secureHash != "" } // Catches an edge-case when the list of signers is empty.
                .map { publicKeyHash -> try {
                    SecureHash.create(publicKeyHash)
                } catch  (e: IllegalArgumentException) {
                    throw SandboxException("Couldn't parse hash $publicKeyHash in serialised evolvable class tag.", e)
                } })

            return EvolvableTagImplV1(
                isNonCpkClass,
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
        entries[IS_NON_CPK_CLASS_IDX] = isNonCpkClass
        entries[CLASS_BUNDLE_NAME_IDX] = classBundleName
        entries[CORDAPP_BUNDLE_NAME_IDX] = cordappBundleName
        entries[CPK_PUBLIC_KEY_HASHES_IDX] = stringifiedCpkPublicKeyHashes

        return entries.joinToString(CLASS_TAG_DELIMITER)
    }
}