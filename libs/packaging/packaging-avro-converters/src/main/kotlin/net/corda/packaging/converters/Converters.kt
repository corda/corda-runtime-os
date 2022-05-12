@file:Suppress("TooManyFunctions")
package net.corda.packaging.converters

import net.corda.data.crypto.SecureHash
import net.corda.data.packaging.CpiIdentifier
import net.corda.data.packaging.CpiMetadata
import net.corda.data.packaging.CpkFormatVersion
import net.corda.data.packaging.CpkIdentifier
import net.corda.data.packaging.CpkManifest
import net.corda.data.packaging.CpkMetadata
import net.corda.data.packaging.CpkType
import net.corda.data.packaging.CorDappManifest
import net.corda.data.packaging.ManifestCorDappInfo
import net.corda.packaging.Cpi
import net.corda.packaging.Cpk
import net.corda.packaging.CordappManifest
import net.corda.packaging.ManifestCordappInfo
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.Collections
import java.util.TreeSet
import java.util.stream.Collector
import java.util.stream.Collectors


fun CpkType.toCorda() = Cpk.Type.valueOf(this.toString())
fun Cpk.Type.toAvro() = CpkType.valueOf(this.toString())

fun CpkIdentifier.toCorda() =
    Cpk.Identifier.newInstance(
        name,
        version,
        signerSummaryHash?.let { net.corda.v5.crypto.SecureHash(it.algorithm, it.serverHash.array()) }
)

fun Cpk.Identifier.toAvro() : CpkIdentifier = CpkIdentifier.newBuilder().also {
    it.name = name
    it.version = version
    it.signerSummaryHash = signerSummaryHash?.let{ hash ->
        SecureHash(hash.algorithm, ByteBuffer.wrap(hash.bytes))
    }
}.build()

fun CpkFormatVersion.toCorda() = Cpk.FormatVersion.newInstance(major,minor)

fun Cpk.FormatVersion.toAvro() : CpkFormatVersion = CpkFormatVersion.newBuilder().also {
    it.major = major
    it.minor = minor
}.build()

fun CpkManifest.toCorda() : Cpk.Manifest = Cpk.Manifest.newInstance(version.toCorda())

fun Cpk.Manifest.toAvro() : CpkManifest = CpkManifest.newBuilder().also {
    it.version = cpkFormatVersion.toAvro()
}.build()

fun ManifestCorDappInfo.toCorda() : ManifestCordappInfo = ManifestCordappInfo(shortName, vendor, versionId, license)

fun ManifestCordappInfo.toAvro() = ManifestCorDappInfo.newBuilder().also {
    it.shortName = shortName
    it.vendor = vendor
    it.versionId = versionId
    it.license = licence
}.build()

fun CorDappManifest.toCorda() : CordappManifest = CordappManifest(
    bundleSymbolicName,
    bundleVersion,
    minPlatformVersion,
    targetPlatformVersion,
    contractInfo.toCorda(),
    workflowInfo.toCorda(),
    attributes
)

fun CordappManifest.toAvro() : CorDappManifest = CorDappManifest.newBuilder().also {
    it.bundleSymbolicName = bundleSymbolicName
    it.bundleVersion = bundleVersion
    it.minPlatformVersion = minPlatformVersion
    it.targetPlatformVersion = targetPlatformVersion
    it.contractInfo = contractInfo.toAvro()
    it.workflowInfo = workflowInfo.toAvro()
    it.attributes = attributes
}.build()

fun CpkMetadata.toCorda() : Cpk.Metadata = Cpk.Metadata.newInstance(
    manifest.toCorda(),
    mainBundle,
    libraries,
    dependencies.stream().map(CpkIdentifier::toCorda).collect(
        Collector.of(::TreeSet,
            TreeSet<Cpk.Identifier>::add,
            { s1 : TreeSet<Cpk.Identifier>, s2: TreeSet<Cpk.Identifier> -> s1.addAll(s2); s1},
            Collections::unmodifiableNavigableSet)),
    corDappManifest.toCorda(),
    type.toCorda(),
    net.corda.v5.crypto.SecureHash(hash.algorithm, hash.serverHash.array()),
    let {
        val crtFactory = CertificateFactory.getInstance("X.509")
        corDappCertificates.stream().map {
            ByteArrayInputStream(it.array())
                .use(crtFactory::generateCertificate)
        }.collect(Collectors.toUnmodifiableSet())
    }
)

fun Cpk.Metadata.toAvro() : CpkMetadata = CpkMetadata.newBuilder().also {
    it.id = id.toAvro()
    it.manifest = manifest.toAvro()
    it.mainBundle = mainBundle
    it.libraries = libraries
    it.dependencies = dependencies.map(Cpk.Identifier::toAvro)
    it.corDappManifest = cordappManifest.toAvro()
    it.type = type.toAvro()
    it.hash = SecureHash(hash.algorithm, ByteBuffer.wrap(hash.bytes))
    it.corDappCertificates = cordappCertificates.stream()
        .map(Certificate::getEncoded)
        .map(ByteBuffer::wrap)
        .collect(Collectors.toUnmodifiableList())
}.build()

fun CpiIdentifier.toCorda() = Cpi.Identifier.newInstance(
    name,
    version,
    signerSummaryHash?.let { net.corda.v5.crypto.SecureHash(it.algorithm, it.serverHash.array()) },
)

fun Cpi.Identifier.toAvro() = CpiIdentifier.newBuilder().also {
    it.name = name
    it.version = version
    it.signerSummaryHash = signerSummaryHash?.let { hash -> SecureHash(hash.algorithm, ByteBuffer.wrap(hash.bytes)) }
}.build()

fun CpiMetadata.toCorda() = Cpi.Metadata.newInstance(
    id.toCorda(),
    net.corda.v5.crypto.SecureHash(hash.algorithm, hash.serverHash.array()),
    cpks.map(CpkMetadata::toCorda),
    groupPolicy
)

fun Cpi.Metadata.toAvro() = CpiMetadata.newBuilder().also {
    it.id = id.toAvro()
    it.hash = SecureHash(hash.algorithm, ByteBuffer.wrap(hash.bytes))
    it.cpks = cpks.map(Cpk.Metadata::toAvro)
    it.groupPolicy = groupPolicy
    it.version = -1 // This value is required for initialization, but isn't used by except by the DB Reconciler.
}.build()
