package net.mailx.android.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.mailx.android.ui.components.AddySectionHeader
import net.mailx.android.ui.components.AliasCard
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.model.FilterUiState
import net.mailx.android.ui.model.UiAlias

/**
 * `anonaddy/addy-android` içindeki `res/layout/fragment_aliases.xml` (`AliasesFragment.kt`)
 * ekranının birebir Compose karşılığı:
 * - Üstte: Material 3 `SearchBar` görünümünde yuvarlatılmış arama çubuğu (`alias_search_bar`)
 * - Yatay `ChipGroup` filtreleri (`alias_chipgroup`: Tümü, Sabitlenen, Aktif, Pasif, Silinen, Wildcard, Özel)
 * - Başlık satırı (`alias_header` + `alias_count` kapsülü + `alias_add_alias` butonu)
 * - `AliasCard` listesi (`aliases_recyclerview_list_item.xml`)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliasesTab(
    aliases: List<UiAlias>,
    isLoading: Boolean,
    filterState: FilterUiState,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectFilterChip: (String) -> Unit,
    onOpenFilterSheet: () -> Unit,
    onSelectAlias: (UiAlias) -> Unit,
    onCopyOrWildcard: (UiAlias) -> Unit,
    onToggleEnabled: (UiAlias, Boolean) -> Unit,
    onOpenCreateSheet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // addy-android alias_search_bar (Material 3 SearchBar kapsül görünümü, çerçevesiz)
        TextField(
            value = filterState.searchQuery,
            onValueChange = onSearchQueryChange,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Ara",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Temizle"
                            )
                        }
                    }
                    IconButton(onClick = onOpenFilterSheet) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filtrele ve Sırala",
                            tint = if (filterState.isCustomFilterActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            },
            placeholder = {
                Text(
                    text = "Takma adlarda ara...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // addy-android alias_chipgroup
        val filterChips = listOf(
            "active_inactive" to "Tümü",
            "pinned_only" to "Sabitlenen",
            "active" to "Aktif",
            "inactive" to "Pasif",
            "deleted" to "Silinen",
            "wildcard_only" to "Wildcard (*)"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterChips.forEach { (key, label) ->
                val isSelected = filterState.selectedChip == key
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectFilterChip(key) },
                    label = { Text(label) }
                )
            }

            FilterChip(
                selected = filterState.isCustomFilterActive,
                onClick = onOpenFilterSheet,
                label = { Text("Özel") }
            )
        }

        // addy-android alias_header + alias_count + alias_add_alias
        AddySectionHeader(
            title = if (filterState.isCustomFilterActive || filterState.searchQuery.isNotEmpty()) {
                "Takma Adlar (Filtreli)"
            } else {
                "Takma Adlar"
            },
            countBadge = aliases.size,
            isLoading = isLoading,
            actionText = "Takma Ad Ekle",
            onActionClick = onOpenCreateSheet,
            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
        )

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (aliases.isEmpty() && !isLoading) {
                EmptyStateBox(
                    icon = Icons.Outlined.AlternateEmail,
                    title = if (filterState.searchQuery.isNotEmpty()) {
                        "\u0022${filterState.searchQuery}\u0022 için sonuç bulunamadı"
                    } else {
                        "Takma ad bulunamadı"
                    },
                    subtitle = "Yeni bir takma ad ekleyebilir veya filtre seçeneklerini değiştirebilirsiniz.",
                    actionLabel = "Takma Ad Ekle",
                    onActionClick = onOpenCreateSheet
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                ) {
                    items(aliases, key = { it.id }) { alias ->
                        AliasCard(
                            alias = alias,
                            modifier = Modifier.animateItem(),
                            onClick = { onSelectAlias(alias) },
                            onCopyOrWildcard = { onCopyOrWildcard(alias) },
                            onToggleEnabled = { newState -> onToggleEnabled(alias, newState) }
                        )
                    }
                }
            }
        }
    }
}
