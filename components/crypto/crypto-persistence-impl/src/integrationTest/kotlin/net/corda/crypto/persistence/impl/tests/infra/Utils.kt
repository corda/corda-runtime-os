package net.corda.crypto.persistence.impl.tests.infra

import org.osgi.framework.BundleContext


inline fun <reified T> BundleContext.getComponent(): T {
    val ref = getServiceReference(T::class.java)
    return getService(ref)
}

