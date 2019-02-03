package org.lodenstone.kurrent.spring.mvc

import org.springframework.context.annotation.Import

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Import(KurrentSpringMvcConfiguration::class)
annotation class EnableKurrentMvc