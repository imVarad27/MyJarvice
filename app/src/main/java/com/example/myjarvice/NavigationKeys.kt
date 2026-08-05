package com.example.myjarvice

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Splash : NavKey
@Serializable data object Welcome : NavKey
@Serializable data object Main : NavKey        // Chat screen
@Serializable data object Settings : NavKey
@Serializable data object Profile : NavKey
