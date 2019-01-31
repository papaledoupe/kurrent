package org.lodenstone.kurrent.spring.eventstore

import org.lodenstone.kurrent.core.aggregate.Command
import org.lodenstone.kurrent.core.eventstore.MapCommandRegistry
import org.lodenstone.kurrent.core.util.loggerFor
import org.lodenstone.kurrent.spring.EnableKurrent
import org.lodenstone.kurrent.spring.getPackagesToScan
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.EnvironmentAware
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotationMetadata
import org.springframework.core.type.filter.AnnotationTypeFilter
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
        scanner.addIncludeFilter(AnnotationTypeFilter(CommandType::class.java)
                .and(AssignableTypeFilter(Command::class.java)))

        val commandMap: Map<String, KClass<*>> = getPackagesToScan(importingClassMetadata)
                .flatMap { packageToScan ->
                    EventTypeRegistrar.logger.debug("Looking for candidate command types in $packageToScan")
                    scanner
                            .findCandidateComponents(packageToScan)
                            .also { logger.debug("Found ${it.size} candidate command types") }
                }
                .associate { candidateComponent ->
                    commandRegistrationData(candidateComponent as AnnotatedBeanDefinition)
                }

        beanDefinitionRegistry.registerBeanDefinition("kurrentMapCommandRegistry", BeanDefinitionBuilder
                .genericBeanDefinition(MapCommandRegistry::class.java) { MapCommandRegistry(commandMap) }
                .beanDefinition)
    }

    private fun commandRegistrationData(beanDefinition: AnnotatedBeanDefinition): Pair<String, KClass<*>> {
        val commandTypeAttributes = beanDefinition.metadata.getAnnotationAttributes(CommandType::class.qualifiedName!!)
        val serializedAs = commandTypeAttributes["name"] as String
        val klass = Class.forName(beanDefinition.beanClassName).kotlin
        logger.debug("Registering command candidate type ${klass.qualifiedName}")
        return serializedAs to klass
    }
}