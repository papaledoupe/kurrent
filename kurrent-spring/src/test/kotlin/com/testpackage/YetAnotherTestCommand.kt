package com.testpackage

import org.lodenstone.kurrent.core.aggregate.Command
import org.lodenstone.kurrent.spring.eventstore.CommandType

@CommandType("YetAnotherTestCommand") object YetAnotherTestCommand : Command