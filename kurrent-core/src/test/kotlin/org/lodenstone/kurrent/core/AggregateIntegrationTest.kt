package org.lodenstone.kurrent.core

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import org.lodenstone.kurrent.core.aggregate.*
import org.lodenstone.kurrent.core.eventstore.EventStoreClient
import org.lodenstone.kurrent.core.eventstore.InMemoryEventStore
import org.lodenstone.kurrent.core.eventstore.write
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(BlockJUnit4ClassRunner::class) class AggregateIntegrationTest {

    data class TestData(val i: Int) {
        data class Create(val i: Int) : Command, Initializing
        data class Created(val i: Int) : Event
        data class SetTo(val i: Int) : Command
        data class Set(val to: Int) : Event
        object IncrementIfLessThanTwo : Command
        object Incremented : Event
    }

    class TestAggregateService(eventStoreClient: EventStoreClient) : AggregateService<TestData>(eventStoreClient) {

        override val aggregateType = "test"
        override val snapshotStore = InMemoryAggregateSnapshotStore<TestData>()
        override fun initializeState() = TestData(i = 0)

        override val aggregateBuilder = aggregate<TestData> {
            command<TestData.IncrementIfLessThanTwo> { data ->
                if (data.i < 2) listOf(TestData.Incremented) else emptyList()
            }
            event<TestData.Incremented> { data ->
                data.copy(i = data.i + 1)
            }
            command<TestData.SetTo> { _, cmd ->
                listOf(TestData.Set(to = cmd.i))
            }
            event<TestData.Set> { data, evt ->
                data.copy(i = evt.to)
            }
            command<TestData.Create> { _, cmd ->
                listOf(TestData.Set(to = cmd.i))
            }
            event<TestData.Created> { data, evt ->
                data.copy(i = evt.i)
            }
        }
    }

    private lateinit var eventStoreClient: EventStoreClient
    private lateinit var service: TestAggregateService

    @Before fun setUp() {
        eventStoreClient = InMemoryEventStore()
        service = TestAggregateService(eventStoreClient)
    }

    @Test fun `test loading up aggregate from the event store`() {

        val aggregateId = "asdf"

        eventStoreClient.write(
                TypedAggregateEvent(
                        aggregateInfo = AggregateInfo(service.aggregateType, aggregateId, 1),
                        event = TestData.Incremented
                ),
                TypedAggregateEvent(
                        aggregateInfo = AggregateInfo(service.aggregateType, aggregateId, 2),
                        event = TestData.Incremented
                ))

        val result = service.loadLatestFromEventStore(aggregateId)
        assertTrue { result?.data?.i == 2 }
    }

    @Test fun `test applying command`() {

        val aggregateId = "asdf"

        eventStoreClient.write(TypedAggregateEvent(
                aggregateInfo = AggregateInfo(service.aggregateType, aggregateId, 1),
                event = TestData.Incremented
        ))

        service.handleCommand(aggregateId, 1, TestData.IncrementIfLessThanTwo)

        val result = service.loadLatestFromEventStore(aggregateId)
        assertTrue { result?.info?.version == 2L }
        assertTrue { result?.data?.i == 2 }

        val readModelResult = service.loadLatestFromSnapshotStore(aggregateId)
        assertTrue { readModelResult?.info?.version == 2L }
        assertTrue { readModelResult?.data?.i == 2 }
    }

    @Test fun `test applying command with properties`() {

        val aggregateId = "asdf"

        eventStoreClient.write(TypedAggregateEvent(
                aggregateInfo = AggregateInfo(service.aggregateType, aggregateId, 1),
                event = TestData.Incremented
        ))

        service.handleCommand(aggregateId, 1, TestData.SetTo(i = 4))

        val result = service.loadLatestFromEventStore(aggregateId)
        assertTrue { result?.info?.version == 2L }
        assertTrue { result?.data?.i == 4 }

        val readModelResult = service.loadLatestFromSnapshotStore(aggregateId)
        assertTrue { readModelResult?.info?.version == 2L }
        assertTrue { readModelResult?.data?.i == 4 }
    }

    @Test fun `test creating new via initializing command`() {

        val aggregateId = "asdf"

        service.handleCommand(aggregateId, 0, TestData.Create(i = 1))

        val result = service.loadLatestFromEventStore(aggregateId)
        assertTrue { result?.info?.version == 1L }
        assertTrue { result?.data?.i == 1 }

        val readModelResult = service.loadLatestFromSnapshotStore(aggregateId)
        assertTrue { readModelResult?.info?.version == 1L }
        assertTrue { readModelResult?.data?.i == 1 }
    }

    @Test fun `test cannot use initializing command on existing aggregate`() {

        val aggregateId = "asdf"

        eventStoreClient.write(TypedAggregateEvent(
                aggregateInfo = AggregateInfo(service.aggregateType, aggregateId, 1),
                event = TestData.Created(i = 0)
        ))

        try {
            service.handleCommand(aggregateId, 0, TestData.Create(i = 1))
            fail("expected exception to be thrown")
        } catch (e: AggregateIdConflictException) {
            // pass
        }
    }

    @Test fun `test applying command with old version`() {

        val aggregateId = "asdf"

        eventStoreClient.write(
                TypedAggregateEvent(
                        aggregateInfo = AggregateInfo(service.aggregateType, aggregateId, 1),
                        event = TestData.Incremented
                ),
                TypedAggregateEvent(
                        aggregateInfo = AggregateInfo(service.aggregateType, aggregateId, 2),
                        event = TestData.Incremented
                ))

        try {
            service.handleCommand(aggregateId, 1, TestData.SetTo(i = 4))
            fail("Expected exception to be thrown")
        } catch (e: AggregateVersionConflictException) {
            // pass
        }
    }
}