package net.corda.membership.mtls.allowed.list.service

import net.corda.data.p2p.mtls.MgmAllowedCertificateSubject
import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter

interface AllowedCertificatesReaderWriterService:
    ReconcilerReader<MgmAllowedCertificateSubject, MgmAllowedCertificateSubject>,
    ReconcilerWriter<MgmAllowedCertificateSubject, MgmAllowedCertificateSubject>,
    Lifecycle