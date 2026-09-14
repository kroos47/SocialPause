plugins { `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
val checkRules by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("app.socialpause.engine.EngineTests")
}
tasks.check { dependsOn(checkRules) }
