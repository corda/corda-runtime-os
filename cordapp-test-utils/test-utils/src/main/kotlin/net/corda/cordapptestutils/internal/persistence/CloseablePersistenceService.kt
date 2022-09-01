package net.corda.cordapptestutils.internal.persistence

import net.corda.v5.application.persistence.PersistenceService
import java.io.Closeable

interface CloseablePersistenceService : PersistenceService, Closeable