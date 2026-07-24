plugins {
    id("dev.kikugie.stonecutter")
    id("me.modmuss50.mod-publish-plugin") version "2.1.+" apply false
}

stonecutter active "26.1"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    constants["release"] = property("mod.id") != "template"
    dependencies["fapi"] = node.project.property("deps.fabric_api") as String

    replacements.string(eval(node.metadata.version, ">=26.2")) {
        replace("mc.setScreen(", "mc.gui.setScreen(")
        replace("client.setScreen(", "client.gui.setScreen(")
        replace("Minecraft.getInstance().screen", "Minecraft.getInstance().gui.screen()")
        replace("Minecraft.getInstance().setScreen(", "Minecraft.getInstance().gui.setScreen(")
        replace(
            "import net.minecraft.world.entity.EntityType;",
            "import net.minecraft.world.entity.EntityType;\nimport net.minecraft.world.entity.EntityTypes;"
        )
    }
    replacements.regex(eval(node.metadata.version, ">=26.2")) {
        replace(
            "(?<!\\.)\\b(mc|client)\\.screen\\b" to "$1.gui.screen()",
            "\\b(mc|client)\\.gui\\.screen\\(\\)" to "$1.screen"
        )
        replace(
            "\\bEntityType\\.([A-Z][A-Z0-9_]*)\\b" to "EntityTypes.$1",
            "\\bEntityTypes\\.([A-Z][A-Z0-9_]*)\\b" to "EntityType.$1"
        )
    }

    swaps["legacy_color_check"] = when {
        eval(node.metadata.version, ">=26.2") -> "if (TextColor.fromLegacyFormat(formatting) != null) {"
        else -> "if (formatting.isColor()) {"
    }
}

val releaseVersions = listOf("26.1", "26.2")

stonecutter tasks {
    order("publishMods")
}

tasks.register("publishToAllPlatforms") {
    group       = "publishing"
    description = "Publish all release groups to Modrinth and CurseForge sequentially."
    dependsOn(releaseVersions.map { ":$it:publishMods" })
}

gradle.projectsEvaluated {
    releaseVersions.zipWithNext().forEach { (prev, next) ->
        project(":$next").tasks.matching { it.name == "publishMods" }.configureEach {
            mustRunAfter(project(":$prev").tasks.matching { it.name == "publishMods" })
        }
    }
}
