@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conwic.mixmaster.ui.calculator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.formatArea
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.ui.components.SectionLabel

/**
 * Picks the job the calculator is working on: a room off a live project, and the coat of it
 * being laid today.
 *
 * Tapping a coat brings its recipe, its rate and the number of passes across with the area —
 * everything the Layout tab already knows. Tapping the room itself brings only the area, which
 * is what you want when the mix on the drum isn't the one in the spec.
 */
@Composable
fun JobPickSheet(
    rooms: List<JobRoom>,
    onPick: (JobRoom, JobCoat?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.calc_pick_job),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.calc_pick_job_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
            }

            if (rooms.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.calc_pick_job_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Grouped by project, with the name carried only on the first of its rooms: a crew
            // works one site at a time, and the site's name on every line is noise to scroll past.
            itemsIndexed(rooms, key = { _, room -> room.roomId }) { index, room ->
                // The rooms arrive project by project, so the heading goes on the first of each.
                val first = index == 0 || rooms[index - 1].projectId != room.projectId
                if (first) {
                    SectionLabel(
                        text = room.projectName,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(room, null) }
                        .padding(vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = room.roomName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${formatArea(room.areaM2)} m²",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (room.coats.isEmpty()) {
                        Text(
                            text = stringResource(R.string.calc_pick_job_no_coats),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                room.coats.forEach { coat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(room, coat) }
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "${coat.number}. ${coat.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            // The rate, and how many of it — as "× 2" rather than a word,
                            // because for a self-levelling mix the same figure is millimetres.
                            text = "${formatDecimal(coat.doseGramsPerM2, 1)} g/m²" +
                                (if (coat.quantity != 1.0) " · × ${formatDecimal(coat.quantity, 1)}" else ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
