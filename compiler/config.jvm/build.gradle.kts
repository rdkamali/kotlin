plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.6"

dependencies {
    api(project(":compiler:config"))
    api(project(":core:descriptors.jvm"))
    compileOnly(intellijCoreDep()) { includeJars("asm-all", rootProject = rootProject) }
}

sourceSets {
    "main" { projectDefault() }
    "test" { }
}
