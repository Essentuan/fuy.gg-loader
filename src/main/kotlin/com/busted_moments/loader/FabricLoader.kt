package com.busted_moments.loader

import net.fabricmc.loader.api.FabricLoader

typealias IFabricLoader = FabricLoader

internal object FabricLoader : IFabricLoader by FabricLoader.getInstance()