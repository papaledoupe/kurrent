package org.lodenstone.kurrent.spring.eventstore

import org.springframework.core.type.classreading.MetadataReader
import org.springframework.core.type.classreading.MetadataReaderFactory
import org.springframework.core.type.filter.TypeFilter

internal class AndTypeFilter(vararg val typeFilters: TypeFilter) : TypeFilter {
    override fun match(metadataReader: MetadataReader, metadataReaderFactory: MetadataReaderFactory) =
            typeFilters.all { it.match(metadataReader, metadataReaderFactory) }
}

internal fun TypeFilter.and(other: TypeFilter): TypeFilter = AndTypeFilter(this, other)