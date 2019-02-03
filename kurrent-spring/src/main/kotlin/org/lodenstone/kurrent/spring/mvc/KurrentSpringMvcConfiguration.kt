package org.lodenstone.kurrent.spring.mvc

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration

@Configuration
@Conditional
@ComponentScan(basePackageClasses = [ KurrentSpringMvcConfiguration::class ])
open class KurrentSpringMvcConfiguration