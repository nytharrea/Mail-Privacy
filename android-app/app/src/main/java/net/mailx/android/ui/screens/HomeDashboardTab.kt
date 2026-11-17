package net.mailx.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val totalEvents = totalForwards + totalBlocks + totalReplies + totalSends

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "traffic_summary_card") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
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
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
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

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = "$totalEvents işlem",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // 2x2 Ferah İstatistik Izgarası
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    label = "Engellenen",
                                    value = totalBlocks,
                                    dotColor = AddyPalette.BlockedRed
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricPill(
                                    modifier = Modifier.weight(1f),
                                    label = "Yanıtlanan",
                                    value = totalReplies,
                                    dotColor = AddyPalette.RepliedBlue
                                )
                                MetricPill(
                                    modifier = Modifier.weight(1f),
                                    label = "Gönderilen",
                                    value = totalSends,
                                    dotColor = AddyPalette.SentCyan
                                )
                            }
                        }

                        // Durum Filtre Hapları
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusCountPill(
                                modifier = Modifier.weight(1f),
                                label = "Aktif",
                                count = activeCount,
                                dotColor = AddyPalette.ActiveGreen,
                                onClick = { onNavigateToFilter("active") }
                            )
                            StatusCountPill(
                                modifier = Modifier.weight(1f),
                                label = "Pasif",
                                count = inactiveCount,
                                dotColor = Color(0xFF7D8B99),
                                onClick = { onNavigateToFilter("inactive") }
                            )
                            StatusCountPill(
                                modifier = Modifier.weight(1f),
                                label = "Sabit",
                                count = pinnedCount,
                                dotColor = AddyPalette.PinnedAmber,
                                onClick = { onNavigateToFilter("pinned_only") }
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
                    Button(
                        onClick = onOpenCreateAlias,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yeni Alias", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onOpenReverseAliasTool,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ters-Alias Gönder", fontWeight = FontWeight.SemiBold)
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (aliases.isNotEmpty()) {
                        TextButton(
                            onClick = { onNavigateToFilter("active_inactive") },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = "Tümünü Gör (${aliases.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
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

@Composable
private fun StatusCountPill(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    dotColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
