package org.lodenstone.kurrent.spring

import org.lodenstone.kurrent.spring.eventstore.CommandTypeRegistrar
import org.lodenstone.kurrent.spring.eventstore.EventTypeRegistrar
import org.springframework.context.annotation.Import
import org.springframework.core.type.AnnotationMetadata
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Import(
        EventTypeRegistrar::class,
        CommandTypeRegistrar::class
)
annotation class EnableKurrent(val scanBasePackages: Array<String> = [],
                               val scanBasePackageClasses: Array<KClass<*>> = [])

internal fun getPackagesToScan(importingClassMetadata: AnnotationMetadata): Array<String> {
    val annotationAttributes = importingClassMetadata
            .getAnnotationAttributes(EnableKurrent::class.qualifiedName!!)
    val scanBasePackages = (annotationAttributes["scanBasePackages"] as Array<String>?) ?: emptyArray()
    val scanBasePackageClasses = (annotationAttributes["scanBasePackageClasses"] as Array<Class<*>>?) ?: emptyArray()
    val specifiedPackages = scanBasePackages + scanBasePackageClasses.map { klass -> klass.`package`.name }
    return if (specifiedPackages.isEmpty()) {
        arrayOf(Class.forName(importingClassMetadata.className).`package`.name)
    } else {
        specifiedPackages
    }
}