@file:JvmName("SandboxConstants")

package net.corda.sandbox.internal

import net.corda.v5.crypto.SecureHash
import java.security.MessageDigest
import java.util.Collections.unmodifiableList

// The symbolic names of the bundles that should be public in the platform sandbox.
// Wrapped inside an unmodifiable list to forbid any tampering.
internal val PUBLIC_PLATFORM_BUNDLE_NAMES: List<String> = unmodifiableList(
    listOf(
        "javax.persistence-api",
        "jcl.over.slf4j",
        "net.corda.application",
        "net.corda.base",
        "net.corda.crypto-api",
        "net.corda.flows",
        "net.corda.kotlin-stdlib-jdk7.osgi-bundle",
        "net.corda.kotlin-stdlib-jdk8.osgi-bundle",
        "net.corda.ledger",
        "net.corda.legacy-api",
        "net.corda.persistence",
        "net.corda.serialization",
        "org.apache.aries.spifly.dynamic.bundle",
        "org.apache.felix.framework",
        "org.apache.felix.scr",
        "org.hibernate.orm.core",
        "org.jetbrains.kotlin.osgi-bundle",
        "slf4j.api"
    )
)

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

    // Used as placeholders when generating class tags for platform classes.
    internal const val PLACEHOLDER_CORDAPP_BUNDLE_NAME = "PLATFORM_BUNDLE"
    internal val PLACEHOLDER_HASH = let {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        SecureHash(digest.algorithm, digest.digest("placeholder_hash".toByteArray()))
    }
}

internal const val HASH_ALGORITHM = "SHA-256"