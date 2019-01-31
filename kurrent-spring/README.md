# kurrent-spring

Extensions to [kurrent-core](../kurrent-core) for use with Spring Framework.

## `@EnableKurrent`
Use this annotation to automatically configure kurrent components and register your `Event` and `Command` types.

Packages are scanned according to the following rules:
* If any `scanBasePackages` or `scanBasePackageClasses` provided, scans packages specified in `scanBasePackages` and packages of classes specified in `scanBasePackageClasses`.
* If none provided, scan from the package of the class annotated with `@EnableKurrent`.