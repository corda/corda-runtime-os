@file:JvmName("SandboxConstants")

package net.corda.sandbox.internal

import net.corda.v5.crypto.SecureHash

// The index of the class tag identifier and version in all serialised class tags.
internal const val CLASS_TAG_IDENTIFIER_IDX = 0
internal const val CLASS_TAG_VERSION_IDX = 1
// We cannot use ';' (because this can appear in bundle symbolic names - see
// http://docs.osgi.org/specification/osgi.core/7.0.0/framework.module.html#framework.module.bsn) or ':' (because this
// appears in stringified secure hashes). '$' cannot appear in bundle symbolic names (see
// http://docs.osgi.org/specification/osgi.core/7.0.0/ch01.html#framework.general.syntax).
internal const val CLASS_TAG_DELIMITER = "$"

// Constants used in class tag serialisation and deserialisation.
internal object ClassTagV1 {
    internal const val STATIC_IDENTIFIER = "S"
    internal const val EVOLVABLE_IDENTIFIER = "E"

    // Used as placeholders when generating class tags for public sandbox classes.
    internal const val PLACEHOLDER_MAIN_BUNDLE_NAME = "PUBLIC_BUNDLE"
    internal val PLACEHOLDER_HASH = SecureHash.create("SHA-256:0000000000000000")
}