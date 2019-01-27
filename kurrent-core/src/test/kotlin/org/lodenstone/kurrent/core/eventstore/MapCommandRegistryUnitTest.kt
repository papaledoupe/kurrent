package org.lodenstone.kurrent.core.eventstore

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.lodenstone.kurrent.core.aggregate.Command

class MapCommandRegistryUnitTest {

    class ClassCommand : Command
    object ObjectCommand : Command
    class MissingCommand : Command

    private val mapCommandRegistry = MapCommandRegistry(mapOf(
            "ClassCommandName" to ClassCommand::class,
            "ObjectCommandName" to ObjectCommand::class
    ))

    @Test fun `get command type by name`() {
        assertThat(mapCommandRegistry.classForCommandName("ClassCommandName"), `is`(ClassCommand::class))
        assertThat(mapCommandRegistry.classForCommandName("ObjectCommandName"), `is`(ObjectCommand::class))
        assertThat(mapCommandRegistry.classForCommandName<MissingCommand>("MissingCommandName"), `is`(nullValue()))
    }

    @Test fun `get command name by type`() {
        assertThat(mapCommandRegistry.commandNameForClass(ClassCommand::class), `is`("ClassCommandName"))
        assertThat(mapCommandRegistry.commandNameForClass(ObjectCommand::class), `is`("ObjectCommandName"))
        assertThat(mapCommandRegistry.commandNameForClass(MissingCommand::class), `is`(nullValue()))
    }
}