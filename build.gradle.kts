plugins {
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.minestom:minestom:2026.03.25-1.21.11")
    implementation("com.google.code.gson:gson:2.14.0")
}

application {
    mainClass.set("com.decenter.server.DecenterServer")
}
