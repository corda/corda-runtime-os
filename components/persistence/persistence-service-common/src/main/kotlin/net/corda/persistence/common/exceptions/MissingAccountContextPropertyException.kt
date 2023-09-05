package net.corda.persistence.common.exceptions

class MissingAccountContextPropertyException : Exception("Flow external event context property 'corda.account' not set")
