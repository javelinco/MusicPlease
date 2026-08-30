package com.javelinco.localmusicplayer.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal const val MORE_OPTION_CARD_TAG = "more-option-card"

private data class MoreOption(
    val title: String,
    val description: String,
    val actionLabel: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
internal fun MoreScreen(
    onMusicFolders: () -> Unit,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
) {
    val options = listOf(
        MoreOption(
            title = "Music folders and scanning",
            description = "Choose folders, find device music, rescan, and manage tracks removed from the index.",
            actionLabel = "Manage",
            icon = Icons.Rounded.Folder,
            onClick = onMusicFolders,
        ),
        MoreOption(
            title = "Backup and restore",
            description = "Create USB-visible backups or restore playlists and app settings.",
            actionLabel = "Open",
            icon = Icons.Rounded.Backup,
            onClick = onBackup,
        ),
        MoreOption(
            title = "Appearance",
            description = "Choose light, dark, or system colors and reduce motion.",
            actionLabel = "Customize",
            icon = Icons.Rounded.Palette,
            onClick = onSettings,
        ),
    )

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(options, key = MoreOption::title) { option ->
            MoreOptionCard(option)
        }
    }
}

@Composable
private fun MoreOptionCard(option: MoreOption) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(MORE_OPTION_CARD_TAG),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 360.dp) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OptionIcon(option.icon)
                    OptionCopy(option, Modifier.weight(1f))
                    FilledTonalButton(onClick = option.onClick) {
                        Text(option.actionLabel)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OptionIcon(option.icon)
                        OptionCopy(option, Modifier.weight(1f))
                    }
                    FilledTonalButton(
                        onClick = option.onClick,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(option.actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun OptionCopy(option: MoreOption, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = option.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = option.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
