package org.lodenstone.kurrent.spring.eventstore

import org.lodenstone.kurrent.core.aggregate.Event
import org.lodenstone.kurrent.core.eventstore.MapEventRegistry
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
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.core.type.filter.AssignableTypeFilter
import kotlin.reflect.KClass

internal class EventTypeRegistrar : ImportBeanDefinitionRegistrar, EnvironmentAware {

    companion object {
        val logger = loggerFor<EventTypeRegistrar>()
    }

    private lateinit var environment: Environment

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    override fun registerBeanDefinitions(importingClassMetadata: AnnotationMetadata, beanDefinitionRegistry: BeanDefinitionRegistry) {
        val scanner = ClassPathScanningCandidateComponentProvider(false, environment)
        scanner.addIncludeFilter(AnnotationTypeFilter(EventType::class.java)
                .and(AssignableTypeFilter(Event::class.java)))

        val eventMap: Map<String, KClass<*>> = getPackagesToScan(importingClassMetadata)
                .flatMap { packageToScan ->
                    logger.debug("Looking for candidate event types in $packageToScan")
                    scanner
                            .findCandidateComponents(packageToScan)
                            .also { logger.debug("Found ${it.size} candidate event types") }
                }
                .associate { candidateComponent ->
                    eventRegistrationData(candidateComponent as AnnotatedBeanDefinition)
                }

        beanDefinitionRegistry.registerBeanDefinition("kurrentMapEventRegistry", BeanDefinitionBuilder
                .genericBeanDefinition(MapEventRegistry::class.java) { MapEventRegistry(eventMap) }
                .beanDefinition)
    }

    private fun eventRegistrationData(beanDefinition: AnnotatedBeanDefinition): Pair<String, KClass<*>> {
        val eventTypeAttributes = beanDefinition.metadata.getAnnotationAttributes(EventType::class.qualifiedName!!)
        val serializedAs = eventTypeAttributes["name"] as String
        val klass = Class.forName(beanDefinition.beanClassName).kotlin
        logger.debug("Registering candidate event type ${klass.qualifiedName}")
        return serializedAs to klass
    }
}