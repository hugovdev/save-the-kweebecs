import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    alias(libs.plugins.paperweight)
}

group = "me.hugo.thankmas.savethekweebecs"
version = "1.0-SNAPSHOT"

paperweight.reobfArtifactConfiguration = ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    paperweight.paperDevBundle(libs.versions.paper)
    compileOnly(libs.luck.perms)
    compileOnly(libs.polar.paper)

    // Citizens API
    compileOnly(libs.citizens) {
        exclude(mutableMapOf("group" to "*", "module" to "*"))
    }

    ksp(libs.koin.ksp.compiler)

    // Work on a paper specific library!
    implementation(project(":common-paper"))
}