package org.lodenstone.kurrent.core.aggregate

interface Event
interface Command
interface Initializing

data class AggregateInfo(val type: String,
                         val id: String,
                         val version: Long)

interface Aggregate<D> {
    val data: D
    val info: AggregateInfo
    fun handle(commandVersion: Long, command: Command): List<Event>
    fun apply(event: Event)

    interface Builder<D> {
        fun build(data: D, aggregateInfo: AggregateInfo): Aggregate<D>
    }
}

interface AggregateEvent<T> where T : Event {
    val aggregateInfo: AggregateInfo
    val event: T
    operator fun component1() = aggregateInfo
    operator fun component2() = event
}