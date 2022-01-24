package net.corda.db.core

enum class DbPrivilege {
    /**
     * DDL (Data Definition Language)
     */
    DDL,

    /**
     * DML (Data Manipulation Language)
     */
    DML,
}