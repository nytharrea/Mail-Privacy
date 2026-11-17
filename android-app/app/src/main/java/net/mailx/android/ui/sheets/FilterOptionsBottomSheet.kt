package net.mailx.android.ui.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.AddySectionView
import net.mailx.android.ui.model.AliasSortField
import net.mailx.android.ui.model.FilterUiState

/**
 * `anonaddy/addy-android` içindeki `res/layout/bottomsheet_filter_options_alias.xml`
 * (`FilterOptionsAliasBottomDialogFragment.kt`) alt penceresinin birebir Compose karşılığı.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterOptionsBottomSheet(
    filterState: FilterUiState,
    onApply: (String, AliasSortField, Boolean, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var tempFilter by remember { mutableStateOf(filterState.selectedChip) }
    var tempSort by remember { mutableStateOf(filterState.sortField) }
    var tempDesc by remember { mutableStateOf(filterState.sortDescending) }
    var tempPinnedFirst by remember { mutableStateOf(filterState.pinnedFirst) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Filtreleme ve Sıralama",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Takma ad listesini durumuna göre filtreleyin veya istediğiniz ölçüte göre sıralayın.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Filtreler Başlığı + Filtreyi Temizle (bs_filteroptions_aliases_clear_filter)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Filtreler",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FilledTonalButton(
                        onClick = onReset,
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterAltOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Filtreyi Temizle")
                    }
                }

                // bs_filteroptions_aliases_mbtg
                val filterOptions = listOf(
                    "active_inactive" to "Tümü",
                    "active" to "Aktif",
                    "inactive" to "Pasif",
                    "pinned_only" to "Sabitlenen",
                    "deleted" to "Silinen",
                    "wildcard_only" to "Wildcard (*)"
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filterOptions.forEach { (key, label) ->
                        FilterChip(
                            selected = tempFilter == key,
                            onClick = { tempFilter = key },
                            label = { Text(label) }
                        )
                    }
                }

                AddySectionView(
                    title = "Sabitlenenleri Üstte Tut",
                    description = "Sıralama ölçütünden bağımsız olarak sabitlenenleri başta göster",
                    startIcon = Icons.Default.PushPin,
                    showSwitch = true,
                    switchChecked = tempPinnedFirst,
                    onSwitchChange = { tempPinnedFirst = it }
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Sıralama Ölçütü Başlığı + Sıralama Yönü Butonu (bs_filteroptions_aliases_sort_order)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sıralama Ölçütü",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FilledTonalButton(
                        onClick = { tempDesc = !tempDesc },
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (tempDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (tempDesc) "Azalan" else "Artan")
                    }
                }

                // bs_filteroptions_aliases_sorting_chipgroup
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AliasSortField.entries.forEach { field ->
                        FilterChip(
                            selected = tempSort == field,
                            onClick = { tempSort = field },
                            label = { Text(field.label) }
                        )
                    }
                }

                // bs_filteroptions_aliases_save_button (CircularProgressButtonSecondary -> 56dp)
                Button(
                    onClick = { onApply(tempFilter, tempSort, tempDesc, tempPinnedFirst) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Kaydet", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
