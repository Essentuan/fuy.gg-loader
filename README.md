# fuy.gg Loader

The mod responsible for loading & updating both Wynntils and fuy.gg. This is a bit cursed as fabric doesn't really support this kind of dynamic mod loading, but it does work and can be extended.

## Extending the loader

To extend the loader first you create a [LanguageAdapter](https://maven.fabricmc.net/docs/fabric-loader-0.16.5/net/fabricmc/loader/api/LanguageAdapter.html) then add a [Dependency](https://github.com/Essentuan/fuy.gg-loader/blob/master/src/main/kotlin/com/busted_moments/loader/api/Dependency.kt) to the [FuyLoader](https://github.com/Essentuan/fuy.gg-loader/blob/master/src/main/kotlin/com/busted_moments/loader/FuyLoader.kt). And thats it! Your mod will now be automatically updated and loaded.

## Quirks

Because the managed mods are loaded *after* fabric loads mods, any mod that depends on them must be put in `.minecraft/assets/custom`. Doing so means they will be loaded along with the rest of the managed mods.
