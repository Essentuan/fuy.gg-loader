package com.busted_moments.loader.client.config

import com.busted_moments.client.framework.config.Storage
import com.busted_moments.client.framework.config.annotations.Category
import com.busted_moments.client.framework.config.annotations.File
import com.busted_moments.client.framework.config.annotations.Floating
import com.busted_moments.client.framework.config.annotations.Tooltip
import com.busted_moments.client.framework.config.entries.value.Value
import com.busted_moments.loader.FuyLoader
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

@File("loader")
@Category("Loader")
object LoaderConfig : Storage {
    @Floating
    @Value("Auto Update")
    @Tooltip(["Whether to perform automatic updates"])
    var autoUpdate: Boolean = true
        set(value) {
            field = value

            flag(FuyLoader.DISABLE_UPDATE, value)
        }

    @Floating
    @Value("Discard managed mods")
    @Tooltip([
        "Whether to disable mods managed by the Loader in the '.minecraft/mods' folder",
        "Disabled mods will be suffixed with '.disabled'"
    ])
    var managedMods: Boolean = true
        set(value) {
            field = value

            flag(FuyLoader.DISABLE_DISCARD, value)
        }

    private fun flag(path: Path, enabled: Boolean) {
        if (enabled)
            path.deleteIfExists()
        else {
            path.createParentDirectories()
            path.writeBytes(ByteArray(0))
        }
    }
}