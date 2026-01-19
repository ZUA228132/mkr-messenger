plugins {
    kotlin("jvm") version "1.9.20"
    application
    kotlin("plugin.serialization") version "1.9.20"
}

group = "com.pranavajay"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:2.3.6")
    implementation("io.ktor:ktor-server-netty:2.3.6")
    implementation("io.ktor:ktor-server-websockets:2.3.6")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.6")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.6")
    implementation("io.ktor:ktor-server-auth:2.3.6")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.6")
    implementation("io.ktor:ktor-server-cors:2.3.6")
    implementation("io.ktor:ktor-server-status-pages:2.3.6")
    
    // MongoDB
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.0")
    implementation("org.mongodb:bson-kotlinx:4.11.0")
    
    // Redis
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
    
    // JWT
    implementation("com.auth0:java-jwt:4.4.0")
    
    // BCrypt
    implementation("org.mindrot:jbcrypt:0.4")
    
    // SendGrid
    implementation("com.sendgrid:sendgrid-java:4.9.3")
    
    // AWS S3
    implementation("aws.sdk.kotlin:s3:1.0.30")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}

application {
    mainClass.set("com.pranavajay.ApplicationKt")
}

kotlin {
    jvmToolchain(17)
}
