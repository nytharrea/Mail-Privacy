package net.mailx.android.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.mailx.android.ui.components.AliasCard
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.model.FilterUiState
import net.mailx.android.ui.model.UiAlias

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = onSearchQueryChange,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Temizle")
                        }
                    }
                },
                placeholder = {
                    Text(
                        text = "Takma ad veya açıklama ara...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )

            BadgedBox(
                badge = {
                    if (filterState.isCustomFilterActive) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                FilledTonalIconButton(
                    onClick = onOpenFilterSheet,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Sırala ve Filtrele")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val filterItems = listOf(
            "active_inactive" to "Tümü",
            "pinned_only" to "Sabitlenen",
            "active" to "Aktif",
            "inactive" to "Pasif",
            "wildcard_only" to "Wildcard (*)",
            "deleted" to "Silinen (90g)"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterItems.forEach { (key, label) ->
                val isSelected = filterState.selectedChip == key
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectFilterChip(key) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null,
                    label = { Text(label) },
                    shape = MaterialTheme.shapes.small
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Kayıtlar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = aliases.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            AssistChip(
                onClick = onOpenFilterSheet,
                leadingIcon = {
                    Icon(
                        imageVector = if (filterState.sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                },
                label = {
                    Text(
                        text = filterState.sortField.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                shape = MaterialTheme.shapes.small
            )
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (aliases.isEmpty() && !isLoading) {
                EmptyStateBox(
                    icon = Icons.Outlined.AlternateEmail,
                    title = if (filterState.searchQuery.isNotEmpty()) {
                        ""${filterState.searchQuery}" için eşleşme yok"
                    } else {
                        "Bu görünümde takma ad yok"
                    },
                    subtitle = "Yeni bir takma ad oluşturabilir veya aktif filtreyi değiştirebilirsiniz.",
                    actionLabel = "Yeni Alias Oluştur",
                    onActionClick = onOpenCreateSheet
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
