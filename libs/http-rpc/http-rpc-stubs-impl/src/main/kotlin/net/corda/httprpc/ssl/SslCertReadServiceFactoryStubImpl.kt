package net.corda.httprpc.ssl

import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component
class SslCertReadServiceFactoryStubImpl @Activate constructor() : SslCertReadServiceFactory {

    override fun create(): SslCertReadService {
        return SslCertReadServiceStubImpl()
    }
}