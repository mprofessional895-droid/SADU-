package com.sadu.assistant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.sadu.assistant.SaduViewModel
import com.sadu.assistant.Tab
import com.sadu.assistant.ui.screens.ConversationsScreen
import com.sadu.assistant.ui.screens.HomeScreen
import com.sadu.assistant.ui.screens.MemoryScreen
import com.sadu.assistant.ui.screens.PhoneActionsScreen
import com.sadu.assistant.ui.screens.SettingsScreen

/**
 * The shell: five sections, a hairline navigation bar at the bottom, and a
 * short cross-fade when you move between them.
 *
 * The bottom bar steps out of the way while the keyboard is up, so the composer
 * sits directly on top of it.
 */
@Composable
fun SaduApp(vm: SaduViewModel) {
    SaduTheme {
        val reduced = rememberReducedMotion()
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        Column(Modifier.fillMaxSize().background(Ink.Background)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                AnimatedContent(
                    targetState = vm.tab,
                    transitionSpec = {
                        val duration = if (reduced) 0 else 240
                        (fadeIn(tween(duration)) +
                            slideInVertically(tween(duration)) { height -> height / 70 })
                            .togetherWith(fadeOut(tween(if (reduced) 0 else 120)))
                    },
                    label = "section",
                ) { tab ->
                    when (tab) {
                        Tab.Home -> HomeScreen(vm)
                        Tab.Conversations -> ConversationsScreen(vm)
                        Tab.Memory -> MemoryScreen(vm)
                        Tab.Actions -> PhoneActionsScreen(vm)
                        Tab.Settings -> SettingsScreen(vm)
                    }
                }
            }
            if (!imeVisible) {
                SaduNavBar(current = vm.tab, onSelect = { vm.selectTab(it) })
            }
        }

        if (vm.perfOpen) {
            PerformancePanel(
                onClose = { vm.closePerformance() },
                onMode = { vm.choosePerfMode(it) },
                backgroundOn = vm.backgroundOn,
                onBackground = { vm.setBackgroundGeneration(it) },
                onUnload = {
                    vm.releaseModelIfIdle("you asked")
                    vm.closePerformance()
                },
            )
        }
    }
}
