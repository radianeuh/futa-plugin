plugins {
    id("zenithproxy.plugin.dev") version "1.0.0-SNAPSHOT"
    id("io.freefair.lombok") version "8.14.2"
}

group = properties["maven_group"] as String
version = properties["plugin_version"] as String
val mc = properties["mc"] as String

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

zenithProxyPlugin {
    templateProperties = mapOf(
        "version" to project.version
    )
    // the minimum supported java version for users of your plugin
    javaReleaseVersion = JavaLanguageVersion.of(25)
}

repositories {
    maven("https://maven.2b2t.vc/releases") {
        description = "ZenithProxy Releases and Dependencies"
    }
    maven("https://maven.2b2t.vc/remote") {
        description = "Dependencies used by ZenithProxy"
    }
}

dependencies {
    zenithProxy("com.zenith:ZenithProxy:$mc-SNAPSHOT")

    shade("com.alibaba.fastjson2:fastjson2:2.0.58")
    shade("cn.hutool:hutool-core:5.8.16")
//    shade("com.alibaba.cola:cola-component-statemachine:5.0.0")

    // Source: https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
    shade("com.fasterxml.jackson.core:jackson-databind:2.21.3")

    /** to include dependencies into your plugin jar **/
//    shade("com.github.ben-manes.caffeine:caffeine:3.2.0")
}

tasks {
    shadowJar {
        /**
         * relocate shaded dependencies to avoid conflicts with other plugins
         * transitive dependencies should also be relocated or removed (with exclude)
         * build and examine your plugin jar contents to check
         * https://gradleup.com/shadow/configuration/relocation/
         */
//        val basePackage = "${project.group}.shadow"
//        relocate("com.github.benmanes.caffeine", "$basePackage.caffeine")

        /**
         * remove unneeded transitive dependencies
         * https://gradleup.com/shadow/configuration/dependencies/#filtering-dependencies
         */
//        dependencies {
//            exclude(dependency(":error_prone_annotations:.*"))
//            exclude(dependency(":jspecify:.*"))
//        }
    }
}
// 1. 强行覆盖 Java 编译任务的 target，确保全盘认准 Java 25
//tasks.withType<JavaCompile>().configureEach {
//    sourceCompatibility = "25"
//    targetCompatibility = "25"
//}

// 2. 核心大招：在 Gradle 解析依赖属性时，强行把当前项目的 JVM 属性伪装/提升为 25
//configurations.all {
//    attributes {
//        // 导入 Gradle 官方的专属 JVM 版本属性键
//        val jvmVersionAttribute = Attribute.of("org.gradle.jvm.version", Int::class.java)
//        attribute(jvmVersionAttribute, 25)
//    }
//}
