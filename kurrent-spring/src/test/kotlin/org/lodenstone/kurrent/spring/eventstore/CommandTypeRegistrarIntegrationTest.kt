package org.lodenstone.kurrent.spring.eventstore

import com.testpackage.TestCommand
import com.testpackage.child.AnotherTestCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.lodenstone.kurrent.core.aggregate.Command
import org.lodenstone.kurrent.core.eventstore.MapCommandRegistry
import org.lodenstone.kurrent.spring.EnableKurrent
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration

class CommandTypeRegistrarIntegrationTest {

    @Test
    fun `registers commands using scanBasePackages`() {

        @EnableKurrent(scanBasePackages = [ "com.testpackage" ])
        @Configuration
        open class Config

        val ctx = AnnotationConfigApplicationContext { register(Config::class.java) }
        ctx.refresh()
        val mapCommandRegistry = ctx.getBean(MapCommandRegistry::class.java)

        assertThat(mapCommandRegistry.classForCommandName<Command>("testCommand"))
                .isNotNull()
                .isEqualTo(TestCommand::class)

        assertThat(mapCommandRegistry.commandNameForClass(TestCommand::class))
                .isNotNull()
                .isEqualTo("testCommand")

        assertThat(mapCommandRegistry.classForCommandName<Command>("another-test-command"))
                .isNotNull()
                .isEqualTo(AnotherTestCommand::class)

        assertThat(mapCommandRegistry.commandNameForClass(AnotherTestCommand::class))
                .isNotNull()
                .isEqualTo("another-test-command")
    }

    @Test
    fun `registers commands using scanBasePackageClasses`() {

        @EnableKurrent(scanBasePackageClasses = [ AnotherTestCommand::class ])
        @Configuration
        open class Config

        val ctx = AnnotationConfigApplicationContext { register(Config::class.java) }
        ctx.refresh()
        val mapCommandRegistry = ctx.getBean(MapCommandRegistry::class.java)

        assertThat(mapCommandRegistry.classForCommandName<Command>("another-test-command"))
                .isNotNull()
                .isEqualTo(AnotherTestCommand::class)

        assertThat(mapCommandRegistry.commandNameForClass(AnotherTestCommand::class))
                .isNotNull()
                .isEqualTo("another-test-command")

        assertThat(mapCommandRegistry.classForCommandName<Command>("testCommand"))
                .isNull()
    }

    @Test fun `registers commands from @EnableKurrent-importing package when no packages specified`() {

        @EnableKurrent
        @Configuration
        open class Config

        val ctx = AnnotationConfigApplicationContext { register(Config::class.java) }
        ctx.refresh()
        val mapCommandRegistry = ctx.getBean(MapCommandRegistry::class.java)

        assertThat(mapCommandRegistry.classForCommandName<Command>("YetAnotherTestCommand"))
                .isNotNull()
                .isEqualTo(YetAnotherTestCommand::class)
        assertThat(mapCommandRegistry.classForCommandName<Command>("testCommand")).isNull()
        assertThat(mapCommandRegistry.classForCommandName<Command>("another-test-command")).isNull()
    }
}