package net.mailx.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mailx.android.ui.components.AddyDonutChart
import net.mailx.android.ui.components.AliasCard
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.components.MetricPill
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.theme.AddyPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardTab(
    aliases: List<UiAlias>,
    isLoading: Boolean,
    defaultDomain: String,
    onRefresh: () -> Unit,
    onNavigateToFilter: (String) -> Unit,
    onSelectAlias: (UiAlias) -> Unit,
    onCopyOrWildcard: (UiAlias) -> Unit,
    onToggleEnabled: (UiAlias, Boolean) -> Unit,
    onOpenCreateAlias: () -> Unit,
    onOpenReverseAliasTool: () -> Unit
) {
    val totalForwards = remember(aliases) { aliases.sumOf { it.forwards } }
    val totalBlocks = remember(aliases) { aliases.sumOf { it.blocks } }
    val totalReplies = remember(aliases) { aliases.sumOf { it.replies } }
    val totalSends = remember(aliases) { aliases.sumOf { it.sends } }

    val activeCount = remember(aliases) { aliases.count { it.enabled } }
    val inactiveCount = remember(aliases) { aliases.count { !it.enabled } }
    val pinnedCount = remember(aliases) { aliases.count { it.pinned } }

    val highlightedAliases = remember(aliases) {
        val pinned = aliases.filter { it.pinned }
        if (pinned.isNotEmpty()) pinned.take(6) else aliases.take(5)
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "traffic_summary_card") {
                OutlinedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "E-posta Trafik Özeti",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${aliases.size} kayıtlı takma ad • @$defaultDomain",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AddyDonutChart(
                                forwards = totalForwards,
                                replies = totalReplies,
                                sends = totalSends,
                                blocks = totalBlocks,
                                enabled = true,
                                size = 56.dp,
                                strokeWidth = 6.dp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricPill(
                                modifier = Modifier.weight(1f),
                                label = "İletilen",
                                value = totalForwards,
                                dotColor = AddyPalette.ForwardedOrange
                            )
                            MetricPill(
                                modifier = Modifier.weight(1f),
                                label = "Engel",
                                value = totalBlocks,
                                dotColor = AddyPalette.BlockedRed
                            )
                            MetricPill(
                                modifier = Modifier.weight(1f),
                                label = "Yanıt",
                                value = totalReplies,
                                dotColor = AddyPalette.RepliedBlue
                            )
                            MetricPill(
                                modifier = Modifier.weight(1f),
                                label = "Gönderim",
                                value = totalSends,
                                dotColor = AddyPalette.SentCyan
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ElevatedAssistChip(
                                onClick = { onNavigateToFilter("active") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AddyPalette.ActiveGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Aktif: $activeCount") },
                                modifier = Modifier.weight(1f)
                            )
                            ElevatedAssistChip(
                                onClick = { onNavigateToFilter("inactive") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PauseCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Pasif: $inactiveCount") },
                                modifier = Modifier.weight(1f)
                            )
                            ElevatedAssistChip(
                                onClick = { onNavigateToFilter("pinned_only") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = AddyPalette.ForwardedOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Sabit: $pinnedCount") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item(key = "quick_actions_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenCreateAlias,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yeni Alias")
                    }
                    OutlinedButton(
                        onClick = onOpenReverseAliasTool,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ters-Alias Gönder")
                    }
                }
            }

            item(key = "highlighted_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (pinnedCount > 0) "Sabitlenen Takma Adlar" else "Son Takma Adlar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (aliases.isNotEmpty()) {
                        TextButton(
                            onClick = { onNavigateToFilter("active_inactive") },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Tümünü Gör (${aliases.size})", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (highlightedAliases.isEmpty()) {
                item(key = "empty_home") {
                    EmptyStateBox(
                        icon = Icons.Outlined.AlternateEmail,
                        title = "Henüz takma ad yok",
                        subtitle = "İlk takma adınızı oluşturarak gerçek e-posta adresinizi koruyun.",
                        actionLabel = "Yeni Alias Oluştur",
                        onActionClick = onOpenCreateAlias
                    )
                }
            } else {
                items(highlightedAliases, key = { "dash_${it.id}" }) { alias ->
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
