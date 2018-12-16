package org.lodenstone.kurrent.core.aggregate


interface AggregateSnapshotStore<A> {
    fun getLatest(aggregateId: String): A?
    fun get(aggregateId: String, aggregateVersion: Long): A?
    fun put(aggregate: Aggregate<A>)
}

class InMemoryAggregateSnapshotStore<A> : AggregateSnapshotStore<A> {

    private var map = mutableMapOf<String, MutableMap<Long, A>>()

    override fun getLatest(aggregateId: String) = map[aggregateId]?.maxBy { (k, _) -> k }?.value

    override fun get(aggregateId: String, aggregateVersion: Long) = map[aggregateId]?.get(aggregateVersion)

    override fun put(aggregate: Aggregate<A>) {
        map.getOrPut(aggregate.info.id, ::mutableMapOf)[aggregate.info.version] = aggregate.data
    }
}