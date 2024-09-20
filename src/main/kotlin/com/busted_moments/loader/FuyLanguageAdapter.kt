package com.busted_moments.loader

import com.busted_moments.loader.api.Dependency
import com.busted_moments.loader.api.ModDownload
import com.busted_moments.loader.http.fetch
import com.busted_moments.loader.release.Candidate
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.LanguageAdapter
import net.fabricmc.loader.api.ModContainer
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.fabricmc.loader.impl.ModContainerImpl
import net.fabricmc.loader.impl.discovery.DirectoryModCandidateFinder
import net.fabricmc.loader.impl.discovery.ModCandidateImpl
import net.fabricmc.loader.impl.discovery.ModDiscoverer
import net.fabricmc.loader.impl.discovery.ModResolver
import net.fabricmc.loader.impl.discovery.RuntimeModRemapper
import net.fabricmc.loader.impl.launch.FabricLauncherBase
import net.fabricmc.loader.impl.metadata.DependencyOverrides
import net.fabricmc.loader.impl.metadata.VersionOverrides
import net.fabricmc.loader.impl.util.SystemProperties
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.writeLines

private val IS_WINDOWS = System.getProperty("os.name").startsWith("Windows")

internal class FuyLanguageAdapter : LanguageAdapter, Dependency {
    private val addCandidate: Method by lazy {
        val method = ModDiscoverer::class.java.declaredMethods.first { it.name == "addCandidateFinder" }
        method.isAccessible = true

        method
    }

    private val addMod: Method by lazy {
        val method = FabricLoaderImpl::class.java.getDeclaredMethod("addMod", ModCandidateImpl::class.java)
        method.isAccessible = true

        method
    }

    private val mods: Field by lazy {
        val field = FabricLoaderImpl::class.java.getDeclaredField("mods")
        field.isAccessible = true

        field
    }

    private val modCandidate: Constructor<ModCandidateImpl> by lazy {
        val constructor = ModCandidateImpl::class.java.declaredConstructors.first()
        constructor.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        constructor as Constructor<ModCandidateImpl>
    }

    init {
        FuyLoader.add(0, this)

        val modsFolder = FabricLoader.gameDir / "mods"

        if (IS_WINDOWS && modsFolder.exists()) {
            for (mod in modsFolder.listDirectoryEntries("*.jar")) {
                try {
                    val entries = ZipFile(mod.toFile()).use {
                        it.stream().toList()
                    }

                    if (entries.size == 1 && entries[0].name == ".discarded")
                        mod.deleteIfExists()
                } catch (ex: Exception) {
                    FuyLoader.error("Error deleting discarded mod ${mod.name}! $ex")
                }
            }
        }

        runBlocking {
            val remapRegularMods = FabricLoader.isDevelopmentEnvironment
            val versionOverrides = VersionOverrides()
            val dependencyOverrides = DependencyOverrides(FabricLoader.configDir)

            val custom = FuyLoader.assets / "custom"
            custom.createDirectories()

            val discoverer = ModDiscoverer(versionOverrides, dependencyOverrides)
            discoverer.load(custom, remapRegularMods)

            val downloads = FuyLoader.download()
            FuyLoader.loaded.addAll(downloads)

            for (download in downloads)
                discoverer.load(download.parent, remapRegularMods)

            val loader = IFabricLoader.getInstance() as FabricLoaderImpl

            val envDisabledMods = mutableMapOf<String, Set<ModCandidateImpl>>()
            val candidates = mutableListOf<ModCandidateImpl>()

            for (candidate in discoverer.discoverMods(loader, envDisabledMods)) {
                when {
                    candidate.isBuiltin -> continue

                    loader.isModLoaded(candidate.id) -> {
                        FuyLoader.warn("Skipping ${candidate.id} as it is already loaded!${if (candidate.isRoot) " It will be disabled on shutdown." else ""}")

                        if (candidate.isRoot) {
                            val container = loader.getModContainer(candidate.id).get()
                            val file = container.origin.paths.first()
                            FuyLoader.preExisting.add(
                                Triple(
                                    container,
                                    file,
                                    file.resolveSibling("${file.name}.disabled")
                                )
                            )
                        }
                    }

                    else -> candidates.add(candidate)
                }
            }

            ModResolver.resolve(
                run {
                    val out = mutableListOf<ModCandidateImpl>()

                    out.addAll(candidates)

                    for (mod in loader.modsInternal)
                        out.add(mod.createCandidate())

                    out
                },
                EnvType.CLIENT,
                envDisabledMods
            )

            val cacheDir = FabricLoader.gameDir / FabricLoaderImpl.CACHE_DIR_NAME
            val outputDir = cacheDir / "processedMods"

            if (remapRegularMods && System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE) != null)
                RuntimeModRemapper.remap(candidates, cacheDir / "tmp", outputDir)

            @Suppress("UNCHECKED_CAST")
            mods.set(
                loader,
                loader.modsInternal.toMutableList()
            )

            for (mod in candidates) {
                if (mod.isRoot)
                    FuyLoader.info("Loading ${mod.id} ${mod.version}!")

                if (!mod.hasPath()) {
                    try {
                        mod.paths = listOf(mod.copyToDir(outputDir, false))
                    } catch (e: IOException) {
                        throw RuntimeException("Error extracting mod $mod", e);
                    }
                }

                for (path in mod.paths)
                    FabricLauncherBase.getLauncher().addToClassPath(path)

                addMod.invoke(
                    IFabricLoader.getInstance(),
                    mod
                )
            }

            if (FuyLoader.preExisting.isNotEmpty() && !FuyLoader.DISABLE_DISCARD.exists()) {
                Runtime.getRuntime().addShutdownHook(thread(start = false) {
                    val log = FabricLoader.gameDir / "logs" / "fuy_loader.log"
                    log.deleteIfExists()

                    fun write(text: String) =
                        log.writeLines(
                            sequenceOf(text),
                            Charsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                        )

                    for ((mod, file, out) in FuyLoader.preExisting) {
                        try {
                            write("Disabling ${mod.metadata.name}!")

                            file.copyTo(out, true)

                            file.outputStream().use {
                                ZipOutputStream(it).use { zip ->
                                    zip.putNextEntry(ZipEntry(".discarded"))
                                    zip.closeEntry()
                                }
                            }

                            if (IS_WINDOWS)
                                Runtime.getRuntime().exec(arrayOf("attrib", "+h", "\"${file.absolutePathString()}\""))
                                    .waitFor()
                            else
                                file.toFile().deleteOnExit()
                        } catch (ex: Exception) {
                            write("Failed to disable ${mod.metadata.name}! $ex")
                        }
                    }
                })
            }
        }
    }

    private fun ModContainerImpl.createCandidate(): ModCandidateImpl {
        return modCandidate.newInstance(
            codeSourcePaths,
            null,
            -1,
            info,
            false,
            listOf<ModCandidateImpl>()
        )
    }

    fun ModDiscoverer.load(path: Path, remap: Boolean) {
        addCandidate.invoke(
            this,
            DirectoryModCandidateFinder(path, remap)
        )
    }

    override fun <T : Any?> create(
        mod: ModContainer?,
        value: String?,
        type: Class<T?>?
    ): T? {
        throw UnsupportedOperationException("Not implemented")
    }

    override val name: String
        get() = "fuy.gg"

    override suspend fun fetch(): List<Pair<String, ModDownload?>> {
        val candidate = fetch<Candidate>("https://thesimpleones.net/buster/release/${FuyLoader.mc}")

        return if (candidate == null)
            listOf("fuy.gg" to null, "wynntils" to null)
        else
            listOf(
                "fuy.gg" to candidate.fuy,
                "wynntils" to candidate.wynntils.last()
            )
    }
}