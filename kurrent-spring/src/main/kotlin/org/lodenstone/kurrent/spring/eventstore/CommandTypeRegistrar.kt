package org.lodenstone.kurrent.spring.eventstore

import org.lodenstone.kurrent.core.aggregate.Command
import org.lodenstone.kurrent.core.eventstore.MapCommandRegistry
import org.lodenstone.kurrent.core.util.loggerFor
import org.lodenstone.kurrent.spring.getPackagesToScan
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.EnvironmentAware
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotationMetadata
import org.springframework.core.type.filter.AssignableTypeFilter
import kotlin.reflect.KClass

internal class CommandTypeRegistrar : ImportBeanDefinitionRegistrar, EnvironmentAware {

    companion object {
        val logger = loggerFor<CommandTypeRegistrar>()
    }

    private lateinit var environment: Environment

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    override fun registerBeanDefinitions(importingClassMetadata: AnnotationMetadata, beanDefinitionRegistry: BeanDefinitionRegistry) {
        val scanner = ClassPathScanningCandidateComponentProvider(false, environment)
        scanner.addIncludeFilter(AssignableTypeFilter(Command::class.java))

        val commandMap: MutableMap<String, KClass<*>> = mutableMapOf()

        getPackagesToScan(importingClassMetadata)
                .flatMap { packageToScan ->
                    logger.debug("Looking for candidate command types in $packageToScan")
                    scanner
                            .findCandidateComponents(packageToScan)
                            .also { logger.debug("Found ${it.size} candidate command types") }
                }
                .forEach { candidateComponent ->
                    val (name, klass) = commandRegistrationData(candidateComponent as AnnotatedBeanDefinition)
                    if (commandMap.containsKey(name)) {
                        throw IllegalStateException("Duplicate command name: $name")
                    }
                    commandMap[name] = klass
                }

        beanDefinitionRegistry.registerBeanDefinition("kurrentMapCommandRegistry", BeanDefinitionBuilder
                .genericBeanDefinition(MapCommandRegistry::class.java) { MapCommandRegistry(commandMap) }
                .beanDefinition)
    }

    private fun commandRegistrationData(beanDefinition: AnnotatedBeanDefinition): Pair<String, KClass<*>> {
        val commandTypeAttributes = beanDefinition.metadata.getAnnotationAttributes(CommandType::class.qualifiedName!!)
        val clazz = Class.forName(beanDefinition.beanClassName, true, Thread.currentThread().contextClassLoader)
        val serializedAs = if (commandTypeAttributes == null) {
            clazz.simpleName
        } else {
            commandTypeAttributes["name"] as String
        }
        val klass = clazz.kotlin
        logger.debug("Registering candidate command type ${klass.qualifiedName} with name $serializedAs")
        return serializedAs to klass
    }
}