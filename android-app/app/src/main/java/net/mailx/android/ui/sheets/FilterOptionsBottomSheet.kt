package net.mailx.android.ui.sheets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mailx.android.ui.components.SectionSwitchRow
import net.mailx.android.ui.model.AliasSortField
import net.mailx.android.ui.model.FilterUiState

@OptIn(ExperimentalMaterial3Api::class)
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
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtrele ve Sırala",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onReset) {
                    Text("Sıfırla")
                }
            }

            Text(
                text = "Durum Filtresi",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "active_inactive" to "Tümü",
                    "active" to "Aktif",
                    "inactive" to "Pasif",
                    "pinned_only" to "Sabitlenen",
                    "wildcard_only" to "Wildcard (*)",
                    "deleted" to "Silinen (90g)"
                ).forEach { (key, lbl) ->
                    FilterChip(
                        selected = tempFilter == key,
                        onClick = { tempFilter = key },
                        label = { Text(lbl) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sıralama Ölçütü",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = { tempDesc = !tempDesc },
                    leadingIcon = {
                        Icon(
                            imageVector = if (tempDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(if (tempDesc) "Azalan" else "Artan") }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AliasSortField.entries.forEach { field ->
                    FilterChip(
                        selected = tempSort == field,
                        onClick = { tempSort = field },
                        label = { Text(field.label) }
                    )
                }
            }

            SectionSwitchRow(
                icon = Icons.Default.PushPin,
                title = "Sabitlenenleri Üstte Tut",
                subtitle = "Sıralamadan bağımsız olarak sabitlenenleri başta göster",
                checked = tempPinnedFirst,
                onCheckedChange = { tempPinnedFirst = it }
            )

            Button(
                onClick = { onApply(tempFilter, tempSort, tempDesc, tempPinnedFirst) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Uygula", fontWeight = FontWeight.Bold)
            }
        }
    }
}
