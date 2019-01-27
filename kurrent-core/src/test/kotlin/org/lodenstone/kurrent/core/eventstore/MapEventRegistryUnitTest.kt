package org.lodenstone.kurrent.core.eventstore

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.lodenstone.kurrent.core.aggregate.Event

class MapEventRegistryUnitTest {

    class ClassEvent : Event
    object ObjectEvent : Event
    class MissingEvent : Event

    private val mapEventRegistry = MapEventRegistry(mapOf(
            "ClassEventName" to ClassEvent::class,
            "ObjectEventName" to ObjectEvent::class
    ))

    @Test fun `get event type by name`() {
        assertThat(mapEventRegistry.classForEventName("ClassEventName"), `is`(ClassEvent::class))
        assertThat(mapEventRegistry.classForEventName("ObjectEventName"), `is`(ObjectEvent::class))
        assertThat(mapEventRegistry.classForEventName<MissingEvent>("MissingEventName"), `is`(nullValue()))
    }

    @Test fun `get event name by type`() {
        assertThat(mapEventRegistry.eventNameForClass(ClassEvent::class), `is`("ClassEventName"))
        assertThat(mapEventRegistry.eventNameForClass(ObjectEvent::class), `is`("ObjectEventName"))
        assertThat(mapEventRegistry.eventNameForClass(MissingEvent::class), `is`(nullValue()))
    }
}