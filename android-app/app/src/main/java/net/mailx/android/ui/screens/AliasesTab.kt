package net.mailx.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.AliasCard
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.components.MailxAliasSkeletonList
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.model.FilterUiState
import net.mailx.android.core.uniffi.AliasRow
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.theme.MailxIcons
import net.mailx.android.ui.theme.MailxPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliasesTab(
    aliases: List<UiAlias>,
    aliasRowsById: Map<String, AliasRow> = emptyMap(),
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

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

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
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = filterState.searchQuery,
            onValueChange = onSearchQueryChange,
            leadingIcon = {
                Icon(
                    imageVector = MailxIcons.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = MailxIcons.X,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            selectionMode = !selectionMode
                            if (!selectionMode) selectedIds = emptySet()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckBox,
                            contentDescription = "Bulk Select",
                            tint = if (selectionMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenFilterSheet,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = MailxIcons.SlidersHorizontal,
                            contentDescription = "Filter and Sort",
                            tint = if (filterState.isCustomFilterActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            placeholder = {
                Text(
                    text = "Search aliases, domains or notes...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        val filterChips = listOf(
            "active_inactive" to "All",
            "pinned_only" to "Pinned",
            "active" to "Active",
            "inactive" to "Disabled",
            "wildcard_only" to "Wildcard (*)",
            "deleted" to "Trash"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            filterChips.forEach { (key, label) ->
                val isSelected = filterState.selectedChip == key
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectFilterChip(key) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    label = {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }

            FilterChip(
                selected = filterState.isCustomFilterActive,
                onClick = onOpenFilterSheet,
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                leadingIcon = {
                    Icon(
                        imageVector = MailxIcons.ArrowUpDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                label = { Text("Sort", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
        }

        AnimatedVisibility(visible = selectionMode) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedIds.size} selected",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                Icon(
                                    Icons.Outlined.DoneAll,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedIds.size == aliases.size) "Clear" else "Select All",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = {
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    MailxIcons.X,
                                    contentDescription = "Close Selection",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
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
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkSetEnabled(idsList, false)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Disable", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkSetPinned(idsList, true)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(MailxIcons.Pin, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Pin", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkSetPinned(idsList, false)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Unpin", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkDelete(idsList)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MailxPalette.BlockedRose.copy(alpha = 0.16f),
                                    contentColor = MailxPalette.BlockedRose
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(MailxIcons.Trash2, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkRestore(idsList)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(MailxIcons.RotateCcw, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                enabled = idsList.isNotEmpty(),
                                onClick = {
                                    onBulkForget(idsList)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MailxPalette.BlockedRose.copy(alpha = 0.16f),
                                    contentColor = MailxPalette.BlockedRose
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(MailxIcons.Trash2, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Forget", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        MailxSectionHeader(
            title = if (filterState.isCustomFilterActive || filterState.searchQuery.isNotEmpty()) {
                "Filtered Aliases"
            } else {
                "Aliases"
            },
            countBadge = aliases.size,
            actionText = "+ New Alias",
            onActionClick = onOpenCreateSheet,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
        )

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && aliases.isEmpty()) {
                MailxAliasSkeletonList(
                    count = 6,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (aliases.isEmpty()) {
                EmptyStateBox(
                    icon = MailxIcons.AtSign,
                    title = if (filterState.searchQuery.isNotEmpty()) {
                        "No aliases match \u0022${filterState.searchQuery}\u0022"
                    } else {
                        "No aliases in this view"
                    },
                    subtitle = "Create a new alias or switch your active filter chip.",
                    actionLabel = "New Alias",
                    onActionClick = onOpenCreateSheet
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 84.dp)
                ) {
                    items(aliases, key = { it.id }, contentType = { "alias_card" }) { alias ->
                        val isSelected = selectedIds.contains(alias.id)
                        AliasCard(
                            alias = alias,
                            aliasRow = aliasRowsById[alias.id],
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
