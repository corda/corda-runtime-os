package net.corda.membership.mtls.allowed.list.service

import net.corda.data.p2p.AllowedCertificateSubject
import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter

interface AllowedCertificatesReaderWriterService:
    ReconcilerReader<AllowedCertificateSubject, AllowedCertificateSubject>,
    ReconcilerWriter<AllowedCertificateSubject, AllowedCertificateSubject>,
    Lifecycle