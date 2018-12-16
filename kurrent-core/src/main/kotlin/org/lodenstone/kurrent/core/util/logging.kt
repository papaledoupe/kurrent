package org.lodenstone.kurrent.core.util

import org.slf4j.LoggerFactory

inline fun <reified T : Any> loggerFor() = LoggerFactory.getLogger(T::class.java)!!