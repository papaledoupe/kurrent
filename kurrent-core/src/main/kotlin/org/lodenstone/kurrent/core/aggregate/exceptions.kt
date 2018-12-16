package org.lodenstone.kurrent.core.aggregate

object AggregateVersionConflictException : RuntimeException()

object AggregateIdConflictException : RuntimeException()

data class NoSuchAggregateException(val aggregateType: String, val aggregateId: String) : RuntimeException()

data class NoSuchAggregateTypeException(val unrecognizedType: String) : RuntimeException()

data class NoSuchCommandException(val unrecognizedCommand: String) : RuntimeException()

data class RejectedCommandException(val reason: String) : RuntimeException(reason)