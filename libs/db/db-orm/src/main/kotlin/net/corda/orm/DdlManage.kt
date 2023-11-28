package net.corda.orm

enum class DdlManage {
    // validate the schema, makes no changes to the database.
    VALIDATE,

    // update the schema.
    UPDATE,

    // creates the schema, destroying previous data.
    CREATE,

    // no changes to the database
    NONE;

    companion object {
        @JvmField
        val default = NONE
    }
}
