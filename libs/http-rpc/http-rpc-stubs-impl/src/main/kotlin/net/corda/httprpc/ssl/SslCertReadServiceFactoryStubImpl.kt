package net.corda.httprpc.ssl

import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [SslCertReadServiceFactory::class], scope = ServiceScope.SINGLETON)
class SslCertReadServiceFactoryStubImpl @Activate constructor() : SslCertReadServiceFactory {

    override fun create(): SslCertReadService {
        return SslCertReadServiceStubImpl()
    }
}