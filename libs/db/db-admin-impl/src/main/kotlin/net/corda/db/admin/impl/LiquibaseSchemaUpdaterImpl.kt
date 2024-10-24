package net.corda.db.admin.impl

import liquibase.Contexts
import liquibase.GlobalConfiguration
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.Scope
import liquibase.UpdateSummaryEnum
import liquibase.UpdateSummaryOutputEnum
import liquibase.command.CommandScope
import liquibase.command.core.TagCommandStep
import liquibase.command.core.UpdateCommandStep
import liquibase.command.core.UpdateSqlCommandStep
import liquibase.command.core.helpers.ChangeExecListenerCommandStep
import liquibase.command.core.helpers.DatabaseChangelogCommandStep
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep
import liquibase.command.core.helpers.ShowSummaryArgument
import net.corda.db.admin.LiquibaseSchemaUpdater
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.output.WriterOutputStream
import java.io.Writer

class LiquibaseSchemaUpdaterImpl(
    private val commandScopeFactory: (commandNames: Array<String>) -> CommandScope = { commandNames ->
        @Suppress("SpreadOperator")
        CommandScope(*commandNames)
    }
): LiquibaseSchemaUpdater {
    override fun update(
        lb: Liquibase,
        sql: Writer?,
        tag: String?
    ) {
        val scopeObjects = mapOf(
            Scope.Attr.resourceAccessor.name to lb.resourceAccessor,
            // This is a temporary workaround till OSGi Manifest is fixed in the next LB version
            Scope.Attr.classLoader.name to FilenameUtils::class.java.classLoader
        )
        Scope.child(scopeObjects) {
            val classLoaderScopeObjects = mapOf(Scope.Attr.classLoader.name to Liquibase::class.java.classLoader)
            if (sql != null) {
                commandScopeFactory(UpdateSqlCommandStep.COMMAND_NAME).configure(lb, tag).also {
                    it.setOutput(
                        WriterOutputStream.Builder().apply {
                            setWriter(sql)
                            setCharset(GlobalConfiguration.OUTPUT_FILE_ENCODING.currentValue)
                        }.get()
                    )
                    Scope.child(classLoaderScopeObjects) {
                        it.execute()
                    }
                }
            } else {
                commandScopeFactory(UpdateCommandStep.COMMAND_NAME).configure(lb, tag).also {
                    Scope.child(classLoaderScopeObjects) {
                        it.execute()
                    }
                }
            }
        }
    }
}

private fun CommandScope.configure(lb: Liquibase, tag: String?): CommandScope {
    val command = this.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, lb.database)
        .addArgumentValue(UpdateCommandStep.CHANGELOG_ARG, lb.databaseChangeLog)
        .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, lb.changeLogFile)
        .addArgumentValue(UpdateCommandStep.CONTEXTS_ARG, Contexts().toString())
        .addArgumentValue(UpdateCommandStep.LABEL_FILTER_ARG, LabelExpression().originalString)
        .addArgumentValue(
            ChangeExecListenerCommandStep.CHANGE_EXEC_LISTENER_ARG,
            lb.defaultChangeExecListener
        )
        .addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY_OUTPUT, UpdateSummaryOutputEnum.LOG)
        .addArgumentValue(
            DatabaseChangelogCommandStep.CHANGELOG_PARAMETERS,
            lb.changeLogParameters
        )
        .addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY, UpdateSummaryEnum.SUMMARY)
    tag?.let {
        command.addArgumentValue(TagCommandStep.TAG_ARG, tag)
    }
    return command
}