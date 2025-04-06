
*** Tinylang ***

Tinylang is a tiny demonstration language for Truffle.
The intention is to create the smallest possible useful Truffle language.
This language is *not* intended for for production-use.

_Important:_ You need Truffle 25.0-ea and a GraalVM JDK 25-ea to build and run this language.

*** Instructions ***

1. Download latest GraalVM: https://www.graalvm.org/docs/reference-manual/
2. Download IGV: https://www.oracle.com/downloads/graalvm-downloads.html
3. set JAVA_HOME to the unpacked GraalVM home folder
4. Clone this repository `git clone git@github.com:chumer/tinylang.git`
5. Maven or other IDE: Import Maven project into your IDE or build with `mvn package`. 
6. Open IGV
7. Run benchmarks on the JVM with `./tiny-jvm ./test/program1.tiny`.
8. Build for native with `./build-native` and then run with `tiny-native ./test/program1.tiny` .
9. Build for web with `./build-web` and then use `./start-web` to start a webserver serving the required files.

