package com.sadu.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sadu.assistant.SaduViewModel
import com.sadu.assistant.PhoneAction
import com.sadu.assistant.PhoneActionCatalog
import com.sadu.assistant.ui.Dimens
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.components.ActionRow
import com.sadu.assistant.ui.components.HairlineDivider
import com.sadu.assistant.ui.components.SectionLabel
import com.sadu.assistant.ui.components.SurfaceCard

@Composable
fun PhoneActionsScreen(vm: SaduViewModel) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        vm.refreshRuntime()
        // A permission denied for good shows no dialog, so "Allow" would look dead. Send the
        // user to the one screen that can still turn it on.
        if (result.values.any { !it }) {
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {
            }
        }
    }

    LaunchedEffect(Unit) { vm.refreshRuntime() }

    PhoneActionsContent(
        actions = vm.actionsForDevice(),
        blockerFor = { vm.blockerFor(it) },
        onAllow = { action ->
            action.permission?.let { launcher.launch(arrayOf(it)) }
        },
        onOpenScreenControl = {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: Throwable) {
            }
        },
        onTry = { vm.askForText(it) },
    )
}

@Composable
fun PhoneActionsContent(
    actions: List<PhoneAction>,
    blockerFor: (PhoneAction) -> String?,
    onAllow: (PhoneAction) -> Unit,
    onOpenScreenControl: () -> Unit,
    onTry: (String) -> Unit,
) {
    val groups = PhoneActionCatalog.groups(actions)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        item(key = "intro") {
            Column {
                SectionLabel("Phone actions")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Everything Sadu can do on this phone, and what each one needs first.",
                    color = Ink.Text,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Nothing here is switched on by itself. Sadu asks before it sends a message " +
                        "or places a call, unless you change that in Settings.",
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        groups.forEach { group ->
            item(key = "label-$group") {
                SectionLabel(group, modifier = Modifier.padding(top = 8.dp))
            }
            item(key = "card-$group") {
                SurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
                    val rows = actions.filter { it.group == group }
                    rows.forEachIndexed { index, action ->
                        if (index > 0) HairlineDivider()
                        val blocker = blockerFor(action)
                        val needsPermission = blocker != null && action.permission != null
                        val needsScreenControl = blocker != null && action.accessibility
                        ActionRow(
                            title = action.title,
                            detail = action.detail,
                            status = blocker ?: "Ready",
                            statusOk = blocker == null,
                            actionLabel = when {
                                needsPermission -> "Allow"
                                needsScreenControl -> "Settings"
                                action.example != null -> "Try"
                                else -> null
                            },
                            onAction = when {
                                needsPermission -> ({ onAllow(action) })
                                needsScreenControl -> onOpenScreenControl
                                action.example != null -> ({ onTry(action.example) })
                                else -> null
                            },
                        )
                    }
                }
            }
        }

        item(key = "footer") {
            Column {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Screen control uses Android's accessibility service. Sadu only reads or taps " +
                        "the screen after you give it a command, and you can switch it off at any time.",
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
