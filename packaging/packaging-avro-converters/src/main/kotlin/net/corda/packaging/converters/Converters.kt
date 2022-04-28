@file:Suppress("TooManyFunctions")
package net.corda.packaging.converters

import net.corda.data.crypto.SecureHash
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.packaging.CPIMetadata
import net.corda.data.packaging.CPKFormatVersion
import net.corda.data.packaging.CPKIdentifier
import net.corda.data.packaging.CPKManifest
import net.corda.data.packaging.CPKMetadata
import net.corda.data.packaging.CPKType
import net.corda.data.packaging.CorDappManifest
import net.corda.data.packaging.ManifestCorDappInfo
import net.corda.packaging.CPI
import net.corda.packaging.CPK
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


fun CPKType.toCorda() = CPK.Type.valueOf(this.toString())
fun CPK.Type.toAvro() = CPKType.valueOf(this.toString())

fun CPKIdentifier.toCorda() =
    CPK.Identifier.newInstance(
        name,
        version,
        signerSummaryHash?.let { net.corda.v5.crypto.SecureHash(it.algorithm, it.serverHash.array()) }
)

fun CPK.Identifier.toAvro() : CPKIdentifier = CPKIdentifier.newBuilder().also {
    it.name = name
    it.version = version
    it.signerSummaryHash = signerSummaryHash?.let{ hash ->
        SecureHash(hash.algorithm, ByteBuffer.wrap(hash.bytes))
    }
}.build()

fun CPKFormatVersion.toCorda() = CPK.FormatVersion.newInstance(major,minor)

fun CPK.FormatVersion.toAvro() : CPKFormatVersion = CPKFormatVersion.newBuilder().also {
    it.major = major
    it.minor = minor
}.build()

fun CPKManifest.toCorda() : CPK.Manifest = CPK.Manifest.newInstance(version.toCorda())

fun CPK.Manifest.toAvro() : CPKManifest = CPKManifest.newBuilder().also {
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

fun CPKMetadata.toCorda() : CPK.Metadata = CPK.Metadata.newInstance(
    manifest.toCorda(),
    mainBundle,
    libraries,
    dependencies.stream().map(CPKIdentifier::toCorda).collect(
        Collector.of(::TreeSet,
            TreeSet<CPK.Identifier>::add,
            {s1 : TreeSet<CPK.Identifier>, s2: TreeSet<CPK.Identifier> -> s1.addAll(s2); s1},
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

fun CPK.Metadata.toAvro() : CPKMetadata = CPKMetadata.newBuilder().also {
    it.id = id.toAvro()
    it.manifest = manifest.toAvro()
    it.mainBundle = mainBundle
    it.libraries = libraries
    it.dependencies = dependencies.map(CPK.Identifier::toAvro)
    it.corDappManifest = cordappManifest.toAvro()
    it.type = type.toAvro()
    it.hash = SecureHash(hash.algorithm, ByteBuffer.wrap(hash.bytes))
    it.corDappCertificates = cordappCertificates.stream()
        .map(Certificate::getEncoded)
        .map(ByteBuffer::wrap)
        .collect(Collectors.toUnmodifiableList())
}.build()

fun CPIIdentifier.toCorda() = CPI.Identifier.newInstance(
    name,
    version,
    signerSummaryHash?.let { net.corda.v5.crypto.SecureHash(it.algorithm, it.serverHash.array()) },
)

fun CPI.Identifier.toAvro() = CPIIdentifier.newBuilder().also {
    it.name = name
    it.version = version
    it.signerSummaryHash = signerSummaryHash?.let { hash -> SecureHash(hash.algorithm, ByteBuffer.wrap(hash.bytes)) }
}.build()

fun CPIMetadata.toCorda() = CPI.Metadata.newInstance(
    id.toCorda(),
    net.corda.v5.crypto.SecureHash(hash.algorithm, hash.serverHash.array()),
    cpks.map(CPKMetadata::toCorda),
    groupPolicy
)

fun CPI.Metadata.toAvro() = CPIMetadata.newBuilder().also {
    it.id = id.toAvro()
    it.hash = SecureHash(hash.algorithm, ByteBuffer.wrap(hash.bytes))
    it.cpks = cpks.map(CPK.Metadata::toAvro)
    it.groupPolicy = groupPolicy
    it.version = -1 // This value is required for initialization, but isn't used by except by the DB Reconciler.
}.build()
