package net.corda.sandbox.internal.classtag

import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.CLASS_TAG_DELIMITER
import net.corda.sandbox.internal.CLASS_TAG_IDENTIFIER_IDX
import net.corda.sandbox.internal.CLASS_TAG_VERSION_IDX
import net.corda.sandbox.internal.ClassTagV1
import net.corda.sandbox.internal.ClassTagV1.PLACEHOLDER_HASH
import net.corda.sandbox.internal.ClassTagV1.PLACEHOLDER_STRING
import net.corda.sandbox.internal.classtag.v1.EvolvableTagImplV1
import net.corda.sandbox.internal.classtag.v1.StaticTagImplV1
import net.corda.sandbox.internal.sandbox.CpkSandbox
import org.osgi.framework.Bundle

/** An implementation of [ClassTagFactory]. */
internal class ClassTagFactoryImpl : ClassTagFactory {
    override fun createSerialisedTag(isStaticTag: Boolean, bundle: Bundle?, cpkSandbox: CpkSandbox?): String {
        if (bundle == null) {
            return if (isStaticTag) {
                StaticTagImplV1(ClassType.NonBundleClass, PLACEHOLDER_STRING, PLACEHOLDER_HASH)
            } else {
                EvolvableTagImplV1(ClassType.NonBundleClass, PLACEHOLDER_STRING, PLACEHOLDER_STRING, PLACEHOLDER_HASH)
            }.serialise()
        }

        val bundleName = bundle.symbolicName ?: throw SandboxException(
            "Bundle at ${bundle.location} does not have a symbolic name, preventing serialisation."
        )

        return if (cpkSandbox == null) {
            if (isStaticTag) {
                StaticTagImplV1(ClassType.PublicSandboxClass, bundleName, PLACEHOLDER_HASH)
            } else {
                EvolvableTagImplV1(ClassType.PublicSandboxClass, bundleName, PLACEHOLDER_STRING, PLACEHOLDER_HASH)
            }
        } else {
            if (isStaticTag) {
                StaticTagImplV1(ClassType.CpkSandboxClass, bundleName, cpkSandbox.cpk.metadata.hash)
            } else {
                val mainBundleName = cpkSandbox.mainBundle.symbolicName
                val signerSummaryHash = cpkSandbox.cpk.metadata.id.signerSummaryHash
                EvolvableTagImplV1(
                    ClassType.CpkSandboxClass, bundleName, mainBundleName, signerSummaryHash
                )
            }
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
                "Serialised class tag version $versionString could not be converted to an integer."
            )

        return when {
            type == ClassTagV1.STATIC_IDENTIFIER && version == 1 -> StaticTagImplV1.deserialise(entries)
            type == ClassTagV1.EVOLVABLE_IDENTIFIER && version == 1 -> EvolvableTagImplV1.deserialise(entries)
            else -> throw SandboxException(
                "Serialised class tag had type $type and version $version. This combination is unknown."
            )
        }
    }
}