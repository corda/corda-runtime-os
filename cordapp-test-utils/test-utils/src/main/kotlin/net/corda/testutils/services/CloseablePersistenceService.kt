package net.corda.testutils.services

import net.corda.v5.application.persistence.PersistenceService
import java.io.Closeable

interface CloseablePersistenceService : PersistenceService, Closeable