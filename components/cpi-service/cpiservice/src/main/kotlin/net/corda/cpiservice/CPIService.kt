package net.corda.cpiservice

import com.typesafe.config.Config
import net.corda.lifecycle.Lifecycle


interface CPIService : Lifecycle {

    /**
     * Register for initial snapshot and new CPIs.
     *
     * The provided handler will be invoked when the first initial snapshot of CPIs are available.
     * After this, the handler will be invoked every time a new CPI comes online.
     *
     * The returned handle may be closed to unregister from the configuration read service.
     *
     * @param cpiServiceHandler The user cpi service handler. See [CPIServiceHandler].
     * @return A handle for this registration, which may be closed to unregister from the configuration read service.
     */
    fun registerForUpdates(cpiServiceHandler: CPIServiceHandler) : AutoCloseable

    getCPI(cpiIdentifier: Identifier): CPI
    getCPIIdentifiers(): List<Identifier>
}

/* Do we have the following stored in corda-api or here with the service interface? */

class CPI(val cpiHash: SecureHash,
          val cpiManifest: Manifest,
          val networkPolicy: JSONObject /*or something more friendly? */) {
    getCPKIdentifiers(): List<Identifier>
    getCPK(cpkIdentifier: Identifier): CPK
    getCPK(cpkHash: SecureHash): CPK
}

class CPK(val cpkHash: SecureHash,
          val cpkManifest: Manifest,
          val corDappHash: SecureHash,
          val corDappRootCertificatesAndSubject: NavigableSet<CertificateAndSubject>,
          val corDappManifest: Manifest,
          val libraryDependencies: List<LibraryIdentifier>,
          val cpkDependencies: List<Identifier>) {

    fun getCorDappOutputStream(): OutputStream
    fun getLibraryOutputStream(libraryIdentifier: LibraryIdentifier): OutputStream
}

interface Manifest {
    fun parseSet(attributeName: String): Set<String>
    fun parseInt(attributeName: String): Int
}

fun interface CPIServiceHandler {
    fun initialSnapshotRetrieved(cpiIdentifiers: List<CPIIdentifier>)
    fun onNewCPI(cpiIdentifier: CPIIdentifier)
}

data class Identifier(val symbolicName: String,
                      val version: String,
                      val signers: NavigableSet<CertificateAndSubject>)

// Or just use Identifier instead?
data class LibraryIdentifier(val symbolicName: String,
                             val version: String)