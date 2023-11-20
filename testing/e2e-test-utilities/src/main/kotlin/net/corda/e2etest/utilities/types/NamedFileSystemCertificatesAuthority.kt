package net.corda.e2etest.utilities.types

import net.corda.crypto.test.certificates.generation.FileSystemCertificatesAuthority

class NamedFileSystemCertificatesAuthority(
    ca: FileSystemCertificatesAuthority,
    val name: String,
): FileSystemCertificatesAuthority by ca
