package com.busted_moments.loader

import com.busted_moments.loader.api.Dependency
import com.busted_moments.loader.api.ModDownload
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.api.Version
import net.fabricmc.loader.impl.metadata.DependencyOverrides
import net.fabricmc.loader.impl.metadata.ModMetadataParser
import net.fabricmc.loader.impl.metadata.VersionOverrides
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream


private const val BUFFER_SIZE = 16384
private const val ATTEMPTS = 3

object FuyLoader : Logger by LoggerFactory.getLogger(FuyLoader::class.java) {
    internal val DISABLE_DISCARD = FabricLoader.gameDir / "config" / "fuy" / "DISABLE_DISCARD"
    internal val DISABLE_UPDATE = FabricLoader.gameDir / "config" / "fuy" / "DISABLE_UPDATE"

    private val minecraft = FabricLoader.getModContainer("minecraft").get()
    internal val assets = FabricLoader.gameDir / "assets"
    private val deps = mutableListOf<Dependency>()

    private var downloaded = false

    internal val preExisting = mutableSetOf<Triple<ModContainer, Path, Path>>()
    internal val loaded = mutableSetOf<Path>()

    val mc: String
        get() = minecraft.metadata.version.toString()


    private fun warn(element: Dependency) {
        if (downloaded)
            warn("${element.name} has been added as a required dependency after mods have been downloaded!")
    }

    operator fun plusAssign(element: Dependency) {
        warn(element)

        deps.add(element)
    }

    internal fun add(index: Int, element: Dependency) {
        warn(element)

        deps.add(index, element)
    }

    private val ModDownload.path: Path
        get() =
            assets / id / version / fileName

    @OptIn(ExperimentalPathApi::class)
    suspend fun download(force: Boolean = false): List<Path> {
        downloaded = true

        FuyLoader.info("Checking for latest fuy.gg version...")

        val versionOverrides = VersionOverrides()
        val dependencyOverrides = DependencyOverrides(FabricLoader.configDir)

        val out = mutableListOf<Path>()
        val seen = mutableSetOf<String>()

        for (dep in deps) {
            for ((id, download) in dep.fetch()) {
                if (download != null && !seen.add(download.id))
                    continue

                when {
                    download == null || (!force && DISABLE_UPDATE.exists()) -> {
                        (assets / id).let {
                            if (it.exists())
                                it.listDirectoryEntries()
                            else
                                emptyList<Path>()
                        }
                            .asSequence()
                            .sortedByDescending {
                                Version.parse(it.name.removePrefix("v"))
                            }
                            .flatMap {
                                it.listDirectoryEntries("*.jar")
                            }
                            .firstOrNull {
                                val file = ZipFile(it.toFile())

                                try {
                                    val entry = file.getEntry("fabric.mod.json")

                                    if (entry != null)
                                        file.getInputStream(entry).use { input ->
                                            ModMetadataParser.parseMetadata(
                                                input,
                                                it.absolutePathString(),
                                                emptyList(),
                                                versionOverrides,
                                                dependencyOverrides,
                                                FabricLoader.isDevelopmentEnvironment
                                            )
                                        }.dependencies.firstOrNull {
                                            it.modId == "minecraft"
                                        }?.matches(minecraft.metadata.version) == true
                                    else false
                                } catch (_: Exception) {
                                    false
                                } finally {
                                    file.close()
                                }
                            }?.let {
                                out.add(it)
                            }
                    }

                    download.path.exists() -> out.add(download.path)

                    else -> run<Unit> download@{
                        download.path.createParentDirectories()

                        val start = System.currentTimeMillis()

                        for (i in 1..ATTEMPTS) {
                            try {
                                info("Downloading $id (Attempt $i)...")

                                download.url.openStream().use { input ->
                                    download.path.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }

                                val fileHash = hash(download.path.inputStream())

                                if (download.hash != null && fileHash != download.hash) {
                                    warn("Mismatched hash for $id! Expected: ${download.hash} Found: $fileHash")
                                    continue
                                }

                                out.add(download.path)

                                val duration = Duration.ofMillis(System.currentTimeMillis() - start)
                                    .toString()
                                    .substring(2)
                                    .replace("(\\d[HMS])(?!$)", "$1 ")
                                    .lowercase()

                                info("Finished download of $id in $duration!")

                                return@download
                            } catch (ex: Exception) {
                                warn("Error while downloading $id! Retrying...", ex)
                            }
                        }

                        error("Download for $id has failed! $id will not be loaded.")
                        download.path.parent.deleteRecursively()
                    }
                }
            }
        }

        return out
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun hash(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)

        input.buffered(BUFFER_SIZE).use { stream ->
            var read: Int

            while (
                stream.read(buffer).also {
                    read = it
                } != -1
            ) {
                digest.update(buffer, 0, read)
            }
        }

        return digest.digest().toHexString()
    }
}