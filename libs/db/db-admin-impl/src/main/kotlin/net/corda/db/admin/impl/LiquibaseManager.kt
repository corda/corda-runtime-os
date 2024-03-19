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
import liquibase.io.WriterOutputStream
import java.io.Writer

class LiquibaseManager(
    private val commandScopeFactory: (commandNames: Array<String>) -> CommandScope = { commandNames ->
        @Suppress("SpreadOperator")
        CommandScope(*commandNames)
    }
) {
    /**
     * @param lb The liquibase object to run the update on
     * @param sql The writer to write the sql to
     * @param tag The tag to apply to the change if any
     */
    fun update(
        lb: Liquibase,
        sql: Writer? = null,
        tag: String? = null
    ) {
        val scopeObjects = mapOf(
            Scope.Attr.resourceAccessor.name to lb.resourceAccessor
        )
        Scope.child(scopeObjects) {
            if (sql != null) {
                commandScopeFactory(UpdateSqlCommandStep.COMMAND_NAME).configure(lb, tag).also {
                    it.setOutput(
                        WriterOutputStream(
                            sql,
                            GlobalConfiguration.OUTPUT_FILE_ENCODING.currentValue
                        )
                    )
                    it.execute()
                }
            } else {
                commandScopeFactory(UpdateCommandStep.COMMAND_NAME).configure(lb, tag).also {
                    it.execute()
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