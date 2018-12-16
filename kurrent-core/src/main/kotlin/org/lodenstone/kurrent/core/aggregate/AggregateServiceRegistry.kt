package org.lodenstone.kurrent.core.aggregate

interface AggregateServiceRegistry {
    fun register(service: AggregateService<*>)
    fun serviceForType(type: String): AggregateService<*>?
    fun allServices(): Collection<AggregateService<*>>
}

class MapAggregateServiceRegistry : AggregateServiceRegistry {

    private val typeToService = mutableMapOf<String, AggregateService<*>>()

    override fun register(service: AggregateService<*>){
        typeToService[service.aggregateType] = service
    }
    override fun serviceForType(type: String) = typeToService[type]
    override fun allServices() = typeToService.values
}