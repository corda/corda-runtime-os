package net.corda.db.core

import java.io.Closeable
import javax.sql.DataSource

interface CloseableDataSource: Closeable, DataSource