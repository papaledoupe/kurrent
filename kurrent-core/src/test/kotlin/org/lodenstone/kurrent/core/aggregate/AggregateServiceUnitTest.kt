package org.lodenstone.kurrent.core.aggregate

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.Assert.fail
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.lodenstone.kurrent.core.eventstore.EventStore

class AggregateServiceUnitTest {

    data class TestData(val string: String)

    object Create : Command, Initializing
    object Created : Event
    data class AddChar(val char: Char) : Command
    data class CharAdded(val newString: String) : Event

    class TestAggregateService(eventStore: EventStore, snapshotStore: AggregateSnapshotStore<TestData>)
        : AggregateService<TestData>(eventStore, snapshotStore) {
        override val aggregateType = "test"

        companion object {
            val aggregateBuilder = aggregate<TestData> {
                command<Create> { _, _ ->
                    listOf(Created)
                }
                command<AddChar> { data, cmd ->
                    if (cmd.char == 'z') {
                        throw RejectedCommandException("letter z is not allowed!")
                    }
                    listOf(CharAdded(data.string + cmd.char))
                }
                event<CharAdded> { _, evt ->
                    TestData(string = evt.newString)
                }
            }
        }

        override val aggregateBuilder = TestAggregateService.aggregateBuilder

        override fun initializeState() = TestData(string = "")
    }

    lateinit var eventStoreMock: EventStore
    lateinit var snapshotStoreMock: AggregateSnapshotStore<TestData>
    lateinit var aggregateService: TestAggregateService

    @Before fun setUp() {
        eventStoreMock = mockk(relaxed = true)
        snapshotStoreMock = mockk(relaxed = true)
        aggregateService = TestAggregateService(eventStoreMock, snapshotStoreMock)
    }

    @Test fun `loads aggregate from event store when no snapshot`() {
        every { snapshotStoreMock.getLatest("id") } returns null
        every { eventStoreMock.findAllEvents("test", "id" )} returns listOf(
                TypedAggregateEvent(AggregateInfo("test", "id", 1), CharAdded("h")),
                TypedAggregateEvent(AggregateInfo("test", "id", 2), CharAdded("he")),
                TypedAggregateEvent(AggregateInfo("test", "id", 3), CharAdded("hel")),
                TypedAggregateEvent(AggregateInfo("test", "id", 4), CharAdded("hell")),
                TypedAggregateEvent(AggregateInfo("test", "id", 5), CharAdded("hello"))
        )

        val aggregate = aggregateService.loadLatest("id")

        assertThat(aggregate?.data?.string).isEqualTo("hello")
    }

    @Test fun `populates snapshot store when loading aggregate from event store`() {
        every { snapshotStoreMock.getLatest("id") } returns null
        every { eventStoreMock.findAllEvents("test", "id" )} returns listOf(
                TypedAggregateEvent(AggregateInfo("test", "id", 1), CharAdded("h")),
                TypedAggregateEvent(AggregateInfo("test", "id", 2), CharAdded("he")),
                TypedAggregateEvent(AggregateInfo("test", "id", 3), CharAdded("hel")),
                TypedAggregateEvent(AggregateInfo("test", "id", 4), CharAdded("hell")),
                TypedAggregateEvent(AggregateInfo("test", "id", 5), CharAdded("hello"))
        )

        aggregateService.loadLatest("id")

        verify(exactly = 1) {
            snapshotStoreMock.put(match {
                it.data.string == "hello" && it.info == AggregateInfo("test", "id", 5)
            })
        }
    }

    @Test fun `loads aggregate partially from event store when outdated snapshot`() {
        every { snapshotStoreMock.getLatest("id") } returns Versioned(TestData("he"), 2)
        every { eventStoreMock.findEventsAfterVersion(any()) } returns listOf(
                TypedAggregateEvent(AggregateInfo("test", "id", 3), CharAdded("hel")),
                TypedAggregateEvent(AggregateInfo("test", "id", 4), CharAdded("hell")),
                TypedAggregateEvent(AggregateInfo("test", "id", 5), CharAdded("hello"))
        )

        val aggregate = aggregateService.loadLatest("id")

        verify(exactly = 1) { eventStoreMock.findEventsAfterVersion(eq(AggregateInfo("test", "id", 2))) }
        assertThat(aggregate?.data?.string).isEqualTo("hello")
    }

    @Test fun `updates snapshot store after assembling partially from outdated snapshot`() {
        every { snapshotStoreMock.getLatest("id") } returns Versioned(TestData("he"), 2)
        every { eventStoreMock.findEventsAfterVersion(any()) } returns listOf(
                TypedAggregateEvent(AggregateInfo("test", "id", 3), CharAdded("hel")),
                TypedAggregateEvent(AggregateInfo("test", "id", 4), CharAdded("hell")),
                TypedAggregateEvent(AggregateInfo("test", "id", 5), CharAdded("hello"))
        )

        aggregateService.loadLatest("id")

        verify(exactly = 1) {
            snapshotStoreMock.put(match {
                it.data.string == "hello" && it.info == AggregateInfo("test", "id", 5)
            })
        }
    }

    @Test fun `returns aggregate snapshot when up-to-date`() {
        every { snapshotStoreMock.getLatest("id") } returns Versioned(TestData("hello"), 5)
        every { eventStoreMock.findEventsAfterVersion(any()) } returns emptyList()

        val aggregate = aggregateService.loadLatest("id")

        verify(exactly = 1) { eventStoreMock.findEventsAfterVersion(eq(AggregateInfo("test", "id", 5))) }
        assertThat(aggregate?.data?.string).isEqualTo("hello")
    }

    @Test fun `does not update snapshot when already up-to-date`() {
        every { snapshotStoreMock.getLatest("id") } returns Versioned(TestData("hello"), 5)
        every { eventStoreMock.findEventsAfterVersion(any()) } returns emptyList()

        aggregateService.loadLatest("id")

        verify(exactly = 0) { snapshotStoreMock.put(any()) }
    }

    @Test(expected = AggregateIdConflictException::class)
    fun `throws AggregateIdConflictException when initializing aggregate already in event store`() {
        every { snapshotStoreMock.getLatest(any()) } returns null
        every { eventStoreMock.findAllEvents(any(), any()) } returns listOf(
                TypedAggregateEvent(AggregateInfo("test", "id", 1), Created)
        )

        aggregateService.handleCommand("id", 1, Create)
    }

    @Test(expected = AggregateIdConflictException::class)
    fun `throws AggregateIdConflictException when initializing aggregate already in snapshot store`() {
        every { snapshotStoreMock.getLatest(any()) } returns Versioned(TestData("hello"), 2)

        aggregateService.handleCommand("id", 1, Create)
    }

    @Test(expected = NoSuchAggregateException::class)
    fun `throws NoSuchAggregateException when applying non-initializing command to aggregate not in event store`() {
        every { snapshotStoreMock.getLatest(any()) } returns null
        every { eventStoreMock.findAllEvents(any(), any()) } returns emptyList()

        aggregateService.handleCommand("id", 1, AddChar('a'))
    }

    @Test fun `can initialize aggregate not in event store with Initializing command`() {
        every { snapshotStoreMock.getLatest(any()) } returns null
        every { eventStoreMock.findAllEvents(any(), any()) } returns emptyList()

        aggregateService.handleCommand("id", 0, Create)

        verify(exactly = 1) {
            eventStoreMock.write(match {
                it.size == 1
                        && it.first().event == Created
                        && it.first().aggregateInfo == AggregateInfo("test", "id", 1)
            })
        }
    }

    @Test fun `writes event to store when applying command does not throw`() {
        every { snapshotStoreMock.getLatest(any()) } returns Versioned(TestData("he"), 2)

        aggregateService.handleCommand("id", 2, AddChar('l'))

        verify(exactly = 1) {
            eventStoreMock.write(match {
                it.size == 1
                        && it.first().event == CharAdded(newString = "hel")
                        && it.first().aggregateInfo == AggregateInfo("test", "id", 3)
            })
        }
    }

    @Test fun `does not write events to store when applying command throws`() {
        every { snapshotStoreMock.getLatest(any()) } returns Versioned(TestData("he"), 2)

        try {
            aggregateService.handleCommand("id", 2, AddChar('z'))
            fail("expected exception to be thrown")
        } catch (rce: RejectedCommandException) {
            // expected
        }

        verify(exactly = 0) { eventStoreMock.write(any()) }
    }

    @Test(expected = AggregateVersionConflictException::class)
    fun `throws AggregateVersionConflictException when applying command with old version`() {
        every { snapshotStoreMock.getLatest(any()) } returns null
        every { eventStoreMock.findAllEvents(any(), any()) } returns listOf(
                TypedAggregateEvent(AggregateInfo("test", "id", 1), Created),
                TypedAggregateEvent(AggregateInfo("test", "id", 2), CharAdded("h"))
        )

        aggregateService.handleCommand("id", 1, AddChar('h'))
    }
}