package org.lodenstone.kurrent.core.aggregate

interface Event
interface Command

interface Initialize : Command
interface Initialized : Event

data class AggregateInfo(val type: String,
                         val id: String,
                         val version: Long) {
    fun incremented() = copy(version = version + 1)
}

interface Aggregate<D> {
    val data: D?
    val info: AggregateInfo
    fun handle(command: Command): List<Event>
    fun apply(event: Event)

    interface Builder<D> {
        fun build(data: D?, aggregateInfo: AggregateInfo): Aggregate<D>
    }
}

interface AggregateEvent<T> where T : Event {
    val aggregateInfo: AggregateInfo
    val event: T
    operator fun component1() = aggregateInfo
    operator fun component2() = event
}