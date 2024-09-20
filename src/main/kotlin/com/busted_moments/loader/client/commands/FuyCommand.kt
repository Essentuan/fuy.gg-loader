@file:Command("fuy")
@file:Alias("fy")

package com.busted_moments.loader.client.commands

import com.busted_moments.client.framework.text.Text.invoke
import com.busted_moments.client.framework.text.FUY_PREFIX
import com.busted_moments.client.framework.text.Text.send
import com.busted_moments.client.inline
import com.busted_moments.loader.FuyLoader
import com.essentuan.acf.core.annotations.Alias
import com.essentuan.acf.core.annotations.Command
import com.essentuan.acf.core.annotations.Subcommand
import com.mojang.brigadier.context.CommandContext
import java.nio.file.Path
import kotlin.io.path.name

private data class ModDetails(
    val id: String,
    val version: String,
    val path: Path
) {
    constructor(file: Path) : this(file.parent.parent.name, file.parent.name, file)
}

@Subcommand("update")
private fun CommandContext<*>.update() {
    inline {
        FUY_PREFIX {
            +"Checking for mod ".gray
            +"updates".aqua
            +"...".gray
        }.send()

        val download = FuyLoader.download(true).map(::ModDetails)

        val before = FuyLoader.loaded
            .asSequence()
            .map {
                val details = ModDetails(it)
                details.id to details
            }
            .toMap(mutableMapOf())

        FuyLoader.loaded.clear()

        for (mod in download) {
            if (before[mod.id] == mod)
                before.remove(mod.id)

            FuyLoader.loaded.add(mod.path)
        }

        if (before.isEmpty()) {
            FUY_PREFIX {
                +"All mods are ".gray
                +"up to date".aqua
                +"!".gray
            }.send()
        } else {
            FUY_PREFIX {
                for (mod in download) {
                    if (mod.id in before)
                        line {
                            +mod.id.aqua
                            +" has been ".gray
                            +"updated".aqua
                            +"! ".gray

                            +before[mod.id]!!.version.aqua
                            +" -> ".gray
                            +mod.version.aqua
                            +".".gray
                        }
                }
            }.send()
        }
    }
}