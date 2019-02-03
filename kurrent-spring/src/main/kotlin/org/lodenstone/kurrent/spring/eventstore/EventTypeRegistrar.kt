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
        scanner.addIncludeFilter(AssignableTypeFilter(Event::class.java))

        val eventMap: MutableMap<String, KClass<*>> = mutableMapOf()

        getPackagesToScan(importingClassMetadata)
                .flatMap { packageToScan ->
                    logger.debug("Looking for candidate event types in $packageToScan")
                    scanner
                            .findCandidateComponents(packageToScan)
                            .also { logger.debug("Found ${it.size} candidate event types") }
                }
                .forEach { candidateComponent ->
                    val (name, klass) = eventRegistrationData(candidateComponent as AnnotatedBeanDefinition)
                    if (eventMap.containsKey(name)) {
                        throw IllegalStateException("Duplicate event name: $name")
                    }
                    eventMap[name] = klass
                }

        beanDefinitionRegistry.registerBeanDefinition("kurrentMapEventRegistry", BeanDefinitionBuilder
                .genericBeanDefinition(MapEventRegistry::class.java) { MapEventRegistry(eventMap.toMap()) }
                .beanDefinition)
    }

    private fun eventRegistrationData(beanDefinition: AnnotatedBeanDefinition): Pair<String, KClass<*>> {
        val eventTypeAttributes = beanDefinition.metadata.getAnnotationAttributes(EventType::class.qualifiedName!!)
        val clazz = Class.forName(beanDefinition.beanClassName, true, Thread.currentThread().contextClassLoader)
        val serializedAs = if (eventTypeAttributes == null) {
            clazz.simpleName
        } else {
            eventTypeAttributes["name"] as String
        }
        val klass = clazz.kotlin
        logger.debug("Registering candidate event type ${klass.qualifiedName} with name $serializedAs")
        return serializedAs to klass
    }
}