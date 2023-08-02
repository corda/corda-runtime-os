package net.corda.crypto.softhsm.impl.infra

import net.corda.crypto.softhsm.TenantInfoService
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

fun makeTenantInfoService(wrappingKeyAlias: String = "root"): TenantInfoService {
    val association = mock<HSMAssociationInfo> {
        on { masterKeyAlias }.thenReturn(wrappingKeyAlias)
    }
    val hsmStore = mock<TenantInfoService> {
        on { lookup(any(), any()) } doReturn association
    }
    return hsmStore
}