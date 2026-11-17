package net.mailx.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.AddySectionHeader
import net.mailx.android.ui.components.AliasCard
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.model.FilterUiState
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.theme.AddyPalette

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
    onBulkSetEnabled: (List<String>, Boolean) -> Unit = { _, _ -> },
    onBulkSetPinned: (List<String>, Boolean) -> Unit = { _, _ -> },
    onBulkDelete: (List<String>) -> Unit = {},
    onBulkRestore: (List<String>) -> Unit = {},
    onBulkForget: (List<String>) -> Unit = {},
    onOpenCreateSheet: () -> Unit
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(aliases, filterState.selectedChip) {
        val validIds = aliases.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(validIds)
        if (aliases.isEmpty()) selectionMode = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = filterState.searchQuery,
            onValueChange = onSearchQueryChange,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear"
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            selectionMode = !selectionMode
                            if (!selectionMode) selectedIds = emptySet()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckBox,
                            contentDescription = "Bulk Select",
                            tint = if (selectionMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = onOpenFilterSheet) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filter and Sort",
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
                    text = "Search aliases...",
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

        val filterChips = listOf(
            "active_inactive" to "All",
            "pinned_only" to "Pinned",
            "active" to "Active",
            "inactive" to "Disabled",
            "deleted" to "Deleted",
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
                label = { Text("Custom") }
            )
        }

        AnimatedVisibility(visible = selectionMode) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedIds.size} selected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = {
                                    selectedIds = if (selectedIds.size == aliases.size) {
                                        emptySet()
                                    } else {
                                        aliases.map { it.id }.toSet()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (selectedIds.size == aliases.size) "Clear All" else "Select All", fontSize = 12.sp)
                            }
                            IconButton(
                                onClick = {
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Selection", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    val idsList = selectedIds.toList()
                    val isDeletedView = filterState.selectedChip == "deleted"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!isDeletedView) {
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkSetEnabled(idsList, true)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Enable", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkSetEnabled(idsList, false)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Disable", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkSetPinned(idsList, true)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pin", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkSetPinned(idsList, false)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Unpin", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkDelete(idsList)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = AddyPalette.SoftRed.copy(alpha = 0.16f),
                                    contentColor = AddyPalette.SoftRed
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", fontSize = 12.sp)
                            }
                        } else {
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkRestore(idsList)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkForget(idsList)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = AddyPalette.SoftRed.copy(alpha = 0.16f),
                                    contentColor = AddyPalette.SoftRed
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Forget", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        AddySectionHeader(
            title = if (filterState.isCustomFilterActive || filterState.searchQuery.isNotEmpty()) {
                "Aliases (Filtered)"
            } else {
                "Aliases"
            },
            countBadge = aliases.size,
            isLoading = isLoading,
            actionText = "New Alias",
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
                        "No results found for \u0022${filterState.searchQuery}\u0022"
                    } else {
                        "No aliases found"
                    },
                    subtitle = "Create a new alias or adjust your filter options.",
                    actionLabel = "New Alias",
                    onActionClick = onOpenCreateSheet
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                ) {
                    items(aliases, key = { it.id }) { alias ->
                        val isSelected = selectedIds.contains(alias.id)
                        AliasCard(
                            alias = alias,
                            modifier = Modifier.animateItem(),
                            selectionMode = selectionMode,
                            isSelected = isSelected,
                            onClick = { onSelectAlias(alias) },
                            onToggleSelect = {
                                selectedIds = if (isSelected) {
                                    selectedIds - alias.id
                                } else {
                                    selectedIds + alias.id
                                }
                            },
                            onCopyOrWildcard = { onCopyOrWildcard(alias) },
                            onToggleEnabled = { newState -> onToggleEnabled(alias, newState) }
                        )
                    }
                }
            }
        }
    }
}
