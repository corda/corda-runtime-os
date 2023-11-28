package net.corda.db.admin

object LiquibaseXmlConstants {
    const val DB_CHANGE_LOG_NS = "http://www.liquibase.org/xml/ns/dbchangelog"
    const val DB_CHANGE_LOG_XSD = "http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd"
    const val DB_CHANGE_LOG_ROOT_ELEMENT = "databaseChangeLog"
    const val DB_CHANGE_LOG_INCLUDE_ELEMENT = "include"
    const val DB_CHANGE_LOG_INCLUDE_FILE_ATTRIBUTE = "file"
}
