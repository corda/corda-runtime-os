package net.corda.sandbox.internal.classtag

import net.corda.sandbox.SandboxException

/** Represents the source of the class in a [ClassTag]. */
internal enum class ClassType {
    /** A class not loaded from a bundle. */
    NonBundleClass {
        override fun toString() = "N" // We use a short representation to avoid bloating the serialised tags.
    },

    /** A class loaded from a CPK sandbox. */
    CpkSandboxClass {
        override fun toString() = "C"
    },

    /** A class loaded from a public sandbox. */
    PublicSandboxClass {
        override fun toString() = "P"
    };

    companion object {
        /** Converts [string] into a [ClassType]. */
        fun fromString(string: String): ClassType {
            return when (string) {
                "C" -> CpkSandboxClass
                "P" -> PublicSandboxClass
                "N" -> NonBundleClass
                else -> throw SandboxException("Could not deserialise class tag class type from string $string.")
            }
        }
    }
}