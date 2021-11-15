package net.corda.libs.permissions.storage.writer.factory

import net.corda.libs.permissions.storage.writer.PermissionStorageWriter
import javax.persistence.EntityManagerFactory

interface PermissionStorageWriterFactory {

    fun create(entityManagerFactory: EntityManagerFactory): PermissionStorageWriter
}