package net.corda.sandbox.internal.classtag.v1

import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.ClassTagV1.CPK_SANDBOX_CLASS
import net.corda.sandbox.internal.ClassTagV1.NON_BUNDLE_CLASS
import net.corda.sandbox.internal.ClassTagV1.PUBLIC_SANDBOX_CLASS
import net.corda.sandbox.internal.classtag.ClassType

// We define the conversions between `ClassType` and strings here, as these may be version-specific.

/** Converts [string] into a [ClassType]. */
internal fun classTypeFromString(string: String): ClassType {
    return when (string) {
        CPK_SANDBOX_CLASS -> ClassType.CpkSandboxClass
        PUBLIC_SANDBOX_CLASS -> ClassType.PublicSandboxClass
        NON_BUNDLE_CLASS -> ClassType.NonBundleClass
        else -> throw SandboxException("Could not deserialise class type from string $string.")
    }
}

/** Converts [ClassType] into a [String]. */
internal fun classTypeToString(classType: ClassType): String {
    return when(classType) {
        ClassType.CpkSandboxClass -> CPK_SANDBOX_CLASS
        ClassType.PublicSandboxClass -> PUBLIC_SANDBOX_CLASS
        ClassType.NonBundleClass -> NON_BUNDLE_CLASS
    }
}