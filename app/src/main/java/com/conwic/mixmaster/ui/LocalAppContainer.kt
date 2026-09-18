package com.conwic.mixmaster.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.conwic.mixmaster.di.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer not provided — wrap the app in CompositionLocalProvider from MainActivity")
}
