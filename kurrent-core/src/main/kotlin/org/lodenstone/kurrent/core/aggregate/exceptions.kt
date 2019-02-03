package org.lodenstone.kurrent.core.aggregate

data class AggregateVersionConflictException(override val message: String?) : RuntimeException()

data class AggregateIdConflictException(override val message: String?) : RuntimeException()

data class NoSuchAggregateException(val aggregateType: String, val aggregateId: String) : RuntimeException("no $aggregateType with ID $aggregateId")

data class NoSuchAggregateTypeException(val unrecognizedType: String) : RuntimeException("no such aggregate type $unrecognizedType")

data class NoSuchCommandException(val unrecognizedCommand: String) : RuntimeException("no such command $unrecognizedCommand")

data class NoSuchEventException(val unrecognizedEvent: String) : RuntimeException("no such command $unrecognizedEvent")

data class RejectedCommandException(override val message: String?) : RuntimeException()