import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.Jdbi
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import org.lodenstone.kurrent.core.aggregate.*
import org.lodenstone.kurrent.core.eventstore.EventStore
import org.lodenstone.kurrent.core.eventstore.MapEventRegistry
import org.lodenstone.kurrent.core.eventstore.write
import org.lodenstone.kurrent.eventstore.sql.JdbiEventStore
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.shaded.com.fasterxml.jackson.annotation.JsonCreator
import org.testcontainers.shaded.com.fasterxml.jackson.annotation.JsonProperty
import java.util.*
import java.util.concurrent.Future

@RunWith(BlockJUnit4ClassRunner::class)
class SqlEventStoreIntegrationTest {

    companion object {
        const val testAggregateType = "TestAggregate"

        @ClassRule @JvmField
        val databaseContainer = KGenericContainer(ImageFromDockerfile()
                .withFileFromClasspath("Dockerfile", "Dockerfile")
                .withFileFromClasspath("schema.sql", "schema.sql"))
                .apply { addExposedPort(3306) }
    }

    interface TestEvent : Event {
        data class Created(val initialData: String) : Initialized
        object NoParams : TestEvent
        data class Params @JsonCreator constructor(@JsonProperty("param") val param: String) : TestEvent
    }

    val testEventRegistry = MapEventRegistry(mapOf(
            "Created" to TestEvent.Created::class,
            "NoParams" to TestEvent.NoParams::class,
            "Params" to TestEvent.Params::class
    ))

    lateinit var client: EventStore

    @Before fun setUp() {
        val port = databaseContainer.getMappedPort(3306)
        val jdbi = Jdbi
                .create("jdbc:mysql://root:root@localhost:$port/event_store")

        client = JdbiEventStore(jdbi, testEventRegistry)
    }

    @Test fun testFindLatestVersion() {
        val id = UUID.randomUUID().toString()
        val aggregateInfo = AggregateInfo(type = testAggregateType, id = id, version = 1)
        writeSampleEvents(aggregateInfo)

        val latestVersion = client.findLatestVersion(aggregateInfo.type, aggregateInfo.id)

        assertThat(latestVersion).isEqualTo(3)
    }

    @Test fun testFindAllEvents() {
        val id = UUID.randomUUID().toString()
        val aggregateInfo = AggregateInfo(type = testAggregateType, id = id, version = 1)
        writeSampleEvents(aggregateInfo)

        val events = client.findAllEvents(aggregateInfo.type, aggregateInfo.id)

        assertThat(events).hasSize(3)
        assertThat(events[0]).matches { it.aggregateInfo == aggregateInfo.copy(version = 1) }
        assertThat(events[0]).matches { it.event is TestEvent.Created }
        assertThat(events[1]).matches { it.aggregateInfo == aggregateInfo.copy(version = 2) }
        assertThat(events[1]).matches { it.event is TestEvent.NoParams }
        assertThat(events[2]).matches { it.aggregateInfo == aggregateInfo.copy(version = 3) }
        assertThat(events[2]).matches { it.event == TestEvent.Params("hello world") }
    }

    @Test fun testFindEventsUpToVersion() {
        val id = UUID.randomUUID().toString()
        val aggregateInfo = AggregateInfo(type = testAggregateType, id = id, version = 1)
        writeSampleEvents(aggregateInfo)

        val events = client.findEventsUpToVersion(aggregateInfo.copy(version = 2))

        assertThat(events).hasSize(2)
        assertThat(events[0]).matches { it.aggregateInfo == aggregateInfo.copy(version = 1) }
        assertThat(events[0]).matches { it.event is Initialized }
        assertThat(events[1]).matches { it.aggregateInfo == aggregateInfo.copy(version = 2) }
        assertThat(events[1]).matches { it.event is TestEvent.NoParams }
    }

    @Test fun testFindEventsAfterVersion() {
        val id = UUID.randomUUID().toString()
        val aggregateInfo = AggregateInfo(type = testAggregateType, id = id, version = 1)
        writeSampleEvents(aggregateInfo)

        val events = client.findEventsAfterVersion(aggregateInfo.copy(version = 2))

        assertThat(events).hasSize(1)
        assertThat(events[0]).matches { it.aggregateInfo == aggregateInfo.copy(version = 3) }
        assertThat(events[0]).matches { it.event == TestEvent.Params("hello world") }
    }

    @Test(expected = AggregateVersionConflictException::class)
    fun writingEventWithSameIdAndVersionThrowsAggregateVersionConflictException() {
        val id = UUID.randomUUID().toString()
        val aggregateInfo = AggregateInfo(type = testAggregateType, id = id, version = 1)

        client.write(TypedAggregateEvent(aggregateInfo, TestEvent.Created("initial data")))
        client.write(TypedAggregateEvent(aggregateInfo, TestEvent.NoParams))
    }

    private fun writeSampleEvents(startingInfo: AggregateInfo) {
        val events = listOf<AggregateEvent<*>>(
                TypedAggregateEvent(startingInfo, TestEvent.Created("initial data")),
                TypedAggregateEvent(startingInfo.incremented(), TestEvent.NoParams),
                TypedAggregateEvent(startingInfo.incremented().incremented(), TestEvent.Params("hello world"))
        )
        client.write(events)
    }
}

// Grr
class KGenericContainer(image: Future<String>) : GenericContainer<KGenericContainer>(image)
