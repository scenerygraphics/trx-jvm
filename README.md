# trx-jvm

A library implementing [TRX (say, tee-ar-ex)](https://github.com/tee-ar-ex) file reading for JVM-based languages.

## Building

Run `./gradlew build` in a terminal in this directory.

## Testing with your own TRX files

Run `./gradlew test --info -Dtrx.filename=path/to/file.trx` from this directory.

## Debug output

trx-jvm uses [slf4j](https://slf4j.org) for logging. You can set the log level via the slf4j backend you use.
The tests use `slf4j-simple`, you can set the log level there via the system property `org.slf4j.simpleLogger.defaultLogLevel`.

## Usage from a Gradle project

There are no releases yet, but you can use [jitpack.io](https://jitpack.io) to depend on the latest trx-jvm repository version in your own project:
```kotlin
repositories {
    // ...
    maven("https://jitpack.io")
}

dependencies {
    // ...
    implementation("com.github.scenerygraphics:trx-jvm:master-SNAPSHOT")
}
```