@file:JvmName("SandboxConstants")

package net.corda.sandbox.internal

import net.corda.v5.crypto.SecureHash

// The index of the class tag identifier, version, tag type and class bundle name in all serialised class tags.
internal const val CLASS_TAG_IDENTIFIER_IDX = 0
internal const val CLASS_TAG_VERSION_IDX = 1
internal const val CLASS_TAG_CLASS_TYPE_IDX = 2
internal const val CLASS_TAG_CLASS_BUNDLE_NAME_IDX = 3

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
    internal const val PLACEHOLDER_STRING = "PLACEHOLDER"
    internal val PLACEHOLDER_HASH = SecureHash.create("SHA-256:0000000000000000")
}

// The symbolic name of the `sandbox-hooks` bundle.
internal const val SANDBOX_HOOKS_BUNDLE = "net.corda.sandbox-hooks"