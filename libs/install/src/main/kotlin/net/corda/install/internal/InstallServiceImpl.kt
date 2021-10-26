package net.corda.install.internal

import net.corda.install.InstallService
import net.corda.install.internal.driver.DriverInstaller
import net.corda.install.internal.persistence.CordaPackagePersistence
import net.corda.install.internal.verification.GroupCpkVerifier
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.AT_LEAST_ONE
import org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY
import java.io.InputStream
import java.nio.file.Path

/** An implementation of the [InstallService] OSGi service interface. */
@Component(service = [InstallService::class])
@Suppress("unused", "LongParameterList")
internal class InstallServiceImpl @Activate constructor(
        @Reference
        private val driverInstaller: DriverInstaller,
        @Reference
        private val cordaPackagePersistence: CordaPackagePersistence,
        @Reference(cardinality = AT_LEAST_ONE, policyOption = GREEDY)
        private val groupVerifiers: List<GroupCpkVerifier>
) : InstallService, SingletonSerializeAsToken {
    override fun installDrivers(driverDirectories: Collection<Path>) = driverInstaller.installDrivers(driverDirectories)

    override fun loadCpb(inputStream: InputStream) = cordaPackagePersistence.putCpb(inputStream)

    override fun loadCpk(cpkHash : SecureHash, inputStream: InputStream) =
        cordaPackagePersistence.get(cpkHash) ?: cordaPackagePersistence.putCpk(inputStream)

    override fun getCpb(cpiIdentifier: CPI.Identifier) = cordaPackagePersistence.get(cpiIdentifier)

    override fun getCpk(id : CPK.Identifier) = cordaPackagePersistence.getCpk(id)

    override fun getCpk(cpkHash: SecureHash) = cordaPackagePersistence.get(cpkHash)

    override fun verifyCpkGroup(cpks: Iterable<CPK>) = groupVerifiers.forEach { verifier -> verifier.verify(cpks.map(CPK::metadata)) }

    override fun getCpbIdentifiers() = cordaPackagePersistence.getCpbIdentifiers()

    override fun hasCpk(cpkHash: SecureHash): Boolean = cordaPackagePersistence.hasCpk(cpkHash)
}
