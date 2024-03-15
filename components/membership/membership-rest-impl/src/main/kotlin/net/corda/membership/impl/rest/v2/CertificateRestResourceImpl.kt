package net.corda.membership.impl.rest.v2

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.rest.v2.CertificateRestResource
import net.corda.rest.PluggableRestResource
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [PluggableRestResource::class])
class CertificateRestResourceImpl @Activate constructor(
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CertificatesClient::class)
    private val certificatesClient: CertificatesClient,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
) : AbstractCertificateRestResourceImpl<CertificateRestResource>(
    cryptoOpsClient,
    keyEncodingService,
    lifecycleCoordinatorFactory,
    certificatesClient,
    virtualNodeInfoReadService,
    platformInfoProvider
),
    CertificateRestResource {

    override val targetInterface = CertificateRestResource::class.java
}
