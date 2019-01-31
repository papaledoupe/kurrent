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

    data class Create(val startingString: String) : Initialize
    data class Created(val startingString: String) : Initialized
    data class AddChar(val char: Char) : Command
    data class AddChars(val chars: List<Char>) : Command
    data class CharAdded(val newString: String) : Event

    class TestAggregateService(eventStore: EventStore, snapshotStore: AggregateSnapshotStore<TestData>)
        : AggregateService<TestData>(eventStore, snapshotStore) {
        override val aggregateType = "test"
        override val aggregateBuilder = aggregate<TestData> {
            initializingCommand<Create> { cmd ->
                Created(startingString = cmd.startingString)
            }
            initializingEvent<Created> { evt ->
                TestData(string = evt.startingString)
            }
            command<AddChar> { cmd ->
                if (cmd.char == 'z') {
                    throw RejectedCommandException("letter z is not allowed!")
                }
                listOf(CharAdded(this.string + cmd.char))
            }
            command<AddChars> { cmd ->
                val events = mutableListOf<Event>()
                cmd.chars.fold(this.string) { str, c ->
                    val newStr = str + c
                    events += CharAdded(newStr)
                    newStr
                }
                events
            }
            event<CharAdded> { evt ->
                TestData(string = evt.newString)
            }
        }
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
                TypedAggregateEvent(AggregateInfo("test", "id", 1), Created(startingString = "h")),
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
                TypedAggregateEvent(AggregateInfo("test", "id", 1), Created(startingString = "h")),
                TypedAggregateEvent(AggregateInfo("test", "id", 2), CharAdded("he")),
                TypedAggregateEvent(AggregateInfo("test", "id", 3), CharAdded("hel")),
                TypedAggregateEvent(AggregateInfo("test", "id", 4), CharAdded("hell")),
                TypedAggregateEvent(AggregateInfo("test", "id", 5), CharAdded("hello"))
        )

        aggregateService.loadLatest("id")

        verify(exactly = 1) {
            snapshotStoreMock.put(
                    eq(AggregateInfo("test", "id", 5)),
                    match { it.string == "hello" })
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
            snapshotStoreMock.put(
                    eq(AggregateInfo("test", "id", 5)),
                    match { it.string == "hello" })
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

        verify(exactly = 0) { snapshotStoreMock.put(any(), any()) }
    }

    @Test(expected = AggregateIdConflictException::class)
    fun `throws AggregateIdConflictException when initializing aggregate already in event store`() {
        every { snapshotStoreMock.getLatest(any()) } returns null
        every { eventStoreMock.findAllEvents(any(), any()) } returns listOf(
                TypedAggregateEvent(AggregateInfo("test", "id", 1), Created(startingString = "h")))

        aggregateService.handleCommand("id", 1, Create(startingString = ""))
    }

    @Test(expected = AggregateIdConflictException::class)
    fun `throws AggregateIdConflictException when initializing aggregate already in snapshot store`() {
        every { snapshotStoreMock.getLatest(any()) } returns Versioned(TestData("hello"), 2)

        aggregateService.handleCommand("id", 1, Create(startingString = ""))
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

        aggregateService.handleCommand("id", 0, Create(startingString = "h"))

        verify(exactly = 1) {
            eventStoreMock.write(match {
                it.size == 1
                        && it.first().event == Created(startingString = "h")
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

    @Test fun `writes in order to store when applying command resulting in multiple events`() {
        every { snapshotStoreMock.getLatest(any()) } returns Versioned(TestData("he"), 2)
        every { eventStoreMock.findEventsAfterVersion(any()) } returns emptyList()

        aggregateService.handleCommand("id", 2, AddChars(listOf('l', 'l', 'o')))

        verify(exactly = 1) {
            eventStoreMock.write(match {
                it.size == 3

                        && it.toList()[0].event == CharAdded(newString = "hel")
                        && it.toList()[0].aggregateInfo == AggregateInfo("test", "id", 3)

                        && it.toList()[1].event == CharAdded(newString = "hell")
                        && it.toList()[1].aggregateInfo == AggregateInfo("test", "id", 4)

                        && it.toList()[2].event == CharAdded(newString = "hello")
                        && it.toList()[2].aggregateInfo == AggregateInfo("test", "id", 5)
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
                TypedAggregateEvent(AggregateInfo("test", "id", 1), Created(startingString = "h")),
                TypedAggregateEvent(AggregateInfo("test", "id", 2), CharAdded("h"))
        )

        aggregateService.handleCommand("id", 1, AddChar('h'))
    }

    @Test
    fun `does not throw AggregateVersionConflictException when applying command with unspecified version`() {
        every { snapshotStoreMock.getLatest(any()) } returns null
        every { eventStoreMock.findAllEvents(any(), any()) } returns listOf(
                TypedAggregateEvent(AggregateInfo("test", "id", 1), Created(startingString = "h")),
                TypedAggregateEvent(AggregateInfo("test", "id", 2), CharAdded("h"))
        )

        aggregateService.handleCommand("id", null, AddChar('e'))
    }
}