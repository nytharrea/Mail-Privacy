package net.mailx.android.ui.sheets
import net.mailx.android.ui.theme.MailxIcons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.MailxSectionRow
import net.mailx.android.ui.model.AliasSortField
import net.mailx.android.ui.model.FilterUiState

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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Filter & Sort Aliases",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Status Filter",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FilledTonalButton(
                        onClick = onReset,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = MailxIcons.FilterOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontSize = 11.sp)
                    }
                }

                val filterOptions = listOf(
                    "active_inactive" to "All",
                    "active" to "Active",
                    "inactive" to "Disabled",
                    "pinned_only" to "Pinned",
                    "wildcard_only" to "Wildcard (*)",
                    "deleted" to "Trash"
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    filterOptions.forEach { (key, label) ->
                        FilterChip(
                            selected = tempFilter == key,
                            onClick = { tempFilter = key },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                MailxSectionRow(
                    title = "Pin Priority",
                    description = "Keep pinned aliases at the top regardless of sort order",
                    startIcon = MailxIcons.Pin,
                    showSwitch = true,
                    switchChecked = tempPinnedFirst,
                    onSwitchChange = { tempPinnedFirst = it }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sort Metric",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FilledTonalButton(
                        onClick = { tempDesc = !tempDesc },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = if (tempDesc) MailxIcons.ArrowDown else MailxIcons.ArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (tempDesc) "Descending" else "Ascending", fontSize = 11.sp)
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AliasSortField.entries.forEach { field ->
                        FilterChip(
                            selected = tempSort == field,
                            onClick = { tempSort = field },
                            label = { Text(field.label, fontSize = 12.sp) }
                        )
                    }
                }

                Button(
                    onClick = { onApply(tempFilter, tempSort, tempDesc, tempPinnedFirst) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = CircleShape
                ) {
                    Text("Apply Filter & Sort", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
