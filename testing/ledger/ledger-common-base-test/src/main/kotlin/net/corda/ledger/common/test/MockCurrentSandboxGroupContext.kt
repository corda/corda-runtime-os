package net.corda.ledger.common.test

import net.corda.crypto.core.SecureHashImpl
import net.corda.internal.serialization.amqp.helper.MockitoHelper
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.crypto.DigestAlgorithmName
import org.mockito.Mockito
import org.osgi.framework.Bundle
import java.time.Instant

internal fun mockCurrentSandboxGroupContext(): CurrentSandboxGroupContext {

    val sandboxGroupContext = Mockito.mock(SandboxGroupContext::class.java)
    val currentSandboxGroupContext = Mockito.mock(CurrentSandboxGroupContext::class.java)
    val mockSandboxGroup = Mockito.mock(SandboxGroup::class.java)

    Mockito.`when`(mockSandboxGroup.metadata).thenReturn(mockCpkMetadata())
    Mockito.`when`(mockSandboxGroup.getEvolvableTag(MockitoHelper.anyObject())).thenReturn("E;bundle;sandbox")
    Mockito.`when`(currentSandboxGroupContext.get()).thenReturn(sandboxGroupContext)
    Mockito.`when`(sandboxGroupContext.sandboxGroup).thenReturn(mockSandboxGroup)

    return currentSandboxGroupContext
}

private fun mockCpkMetadata() = mapOf(
    Mockito.mock(Bundle::class.java) to makeCpkMetadata(1, CordappType.CONTRACT),
    Mockito.mock(Bundle::class.java) to makeCpkMetadata(2, CordappType.WORKFLOW),
    Mockito.mock(Bundle::class.java) to makeCpkMetadata(3, CordappType.CONTRACT),
)

val dummyCpkSignerSummaryHash = SecureHashImpl("TEST", "TEST".toByteArray())

private fun makeCpkMetadata(i: Int, cordappType: CordappType) = CpkMetadata(
    CpkIdentifier("MockCpk", "$i", dummyCpkSignerSummaryHash),
    CpkManifest(CpkFormatVersion(1, 1)),
    "mock-bundle-$i",
    emptyList(),
    CordappManifest(
        "mock-bundle-symbolic",
        "$i",
        1,
        1,
        cordappType,
        "mock-shortname",
        "r3",
        i,
        "None",
        emptyMap()
    ),
    CpkType.UNKNOWN,
    SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32) { i.toByte() }),
    emptySet(),
    Instant.now()
)