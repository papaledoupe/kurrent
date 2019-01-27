# kurrent-store-sql

Implementation of the event store client based on an SQL database, using JDBI. 
The event store [schema](src/main/resources/schema.sql) is also supplied along with docker + docker-compose files to easily run a 
development instance of the event store with MySQL.

If MySQL as your database, a binary log reader implementation is also provided, 
[kurrent-event-store-mysql-cdc](../kurrent-store-mysql-cdc).