# kurrent-spring

Extensions to [kurrent-core](../kurrent-core) for use with Spring Framework.

## `@EnableKurrent`
Use this annotation to automatically configure kurrent components and register your `Event` and `Command` types.

Packages are scanned according to the following rules:
* If any `scanBasePackages` or `scanBasePackageClasses` provided, scans packages specified in `scanBasePackages` and packages of classes specified in `scanBasePackageClasses`.
* If none provided, scan from the package of the class annotated with `@EnableKurrent`.

All types implementing `org.lodenstone.kurrent.core.aggregate.Event` and `org.lodenstone.kurrent.core.aggregate.Command` 
are registered as events and commands, respectively. By default commands events are (de)serialized to their class' 
'simple name' (i.e. without package name). To prevent naming conflicts across different aggregates and to make your code
more resilient to refactoring, it's recommended to also annotate them with `@EventType("..")`/`@CommandType("..")` to
specify an explicit, fixed name.
