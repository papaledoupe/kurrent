# kurrent-store-mysql-cdc

Implementation of the read side of the event store based on change data capture (CDC) from a MySQL event store via the
database's binary log.

Note that this module depends on the *experimental* Kotlin coroutines library.

This is intended to be used with the [kurrent-store-sql](../kurrent-store-sql) module which provides the corresponding 
event store client implementation for SQL databases.