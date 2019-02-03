package org.lodenstone.kurrent.spring.eventstore

import com.testpackage.TestEvent
import com.testpackage.UnannotatedEvent
import com.testpackage.child.AnotherTestEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.lodenstone.kurrent.core.aggregate.Event
import org.lodenstone.kurrent.core.eventstore.MapEventRegistry
import org.lodenstone.kurrent.spring.EnableKurrent
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration

class EventTypeRegistrarIntegrationTest {

    @Test
    fun `registers events using scanBasePackages`() {

        @EnableKurrent(scanBasePackages = [ "com.testpackage" ])
        @Configuration
        open class Config

        val ctx = AnnotationConfigApplicationContext { register(Config::class.java) }
        ctx.refresh()
        val mapEventRegistry = ctx.getBean(MapEventRegistry::class.java)

        assertThat(mapEventRegistry.classForEventName<Event>("testEvent"))
                .isNotNull()
                .isEqualTo(TestEvent::class)

        assertThat(mapEventRegistry.eventNameForClass(TestEvent::class))
                .isNotNull()
                .isEqualTo("testEvent")

        assertThat(mapEventRegistry.classForEventName<Event>("another-test-event"))
                .isNotNull()
                .isEqualTo(AnotherTestEvent::class)

        assertThat(mapEventRegistry.eventNameForClass(AnotherTestEvent::class))
                .isNotNull()
                .isEqualTo("another-test-event")
    }

    @Test
    fun `registers events using scanBasePackageClasses`() {

        @EnableKurrent(scanBasePackageClasses = [ AnotherTestEvent::class ])
        @Configuration
        open class Config

        val ctx = AnnotationConfigApplicationContext { register(Config::class.java) }
        ctx.refresh()
        val mapEventRegistry = ctx.getBean(MapEventRegistry::class.java)

        assertThat(mapEventRegistry.classForEventName<Event>("another-test-event"))
                .isNotNull()
                .isEqualTo(AnotherTestEvent::class)

        assertThat(mapEventRegistry.eventNameForClass(AnotherTestEvent::class))
                .isNotNull()
                .isEqualTo("another-test-event")

        assertThat(mapEventRegistry.classForEventName<Event>("testEvent"))
                .isNull()
    }

    @Test fun `registers events from @EnableKurrent-importing package when no packages specified`() {

        @EnableKurrent
        @Configuration
        open class Config

        val ctx = AnnotationConfigApplicationContext { register(Config::class.java) }
        ctx.refresh()
        val mapEventRegistry = ctx.getBean(MapEventRegistry::class.java)

        assertThat(mapEventRegistry.classForEventName<Event>("YetAnotherTestEvent"))
                .isNotNull()
                .isEqualTo(YetAnotherTestEvent::class)
        assertThat(mapEventRegistry.classForEventName<Event>("testEvent")).isNull()
        assertThat(mapEventRegistry.classForEventName<Event>("another-test-event")).isNull()
    }

    @Test fun `registers unannotated event types by class name`() {

        @EnableKurrent(scanBasePackages = [ "com.testpackage" ])
        @Configuration
        open class Config

        val ctx = AnnotationConfigApplicationContext { register(Config::class.java) }
        ctx.refresh()
        val mapEventRegistry = ctx.getBean(MapEventRegistry::class.java)

        assertThat(mapEventRegistry.eventNameForClass(UnannotatedEvent::class))
                .isNotNull()
                .isEqualTo("UnannotatedEvent")
    }

    @Test(expected = IllegalStateException::class)
    fun `throws IllegalStateException when events have conflicting names`() {

        // Two events explicitly named as YetAnotherTestEvent

        @EnableKurrent(scanBasePackages = [ "com.testpackage", "org.lodenstone" ])
        @Configuration
        open class Config

        val ctx = AnnotationConfigApplicationContext { register(Config::class.java) }
        ctx.refresh()
    }
}