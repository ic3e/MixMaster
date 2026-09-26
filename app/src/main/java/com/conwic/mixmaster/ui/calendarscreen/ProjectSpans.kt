package com.conwic.mixmaster.ui.calendarscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.domain.formatDateRange
import com.conwic.mixmaster.ui.theme.Accent
import com.conwic.mixmaster.ui.theme.Accent2
import com.conwic.mixmaster.ui.theme.ChipShape
import com.conwic.mixmaster.ui.theme.Danger
import com.conwic.mixmaster.ui.theme.Ok
import java.time.LocalDate

/**
 * A project on a calendar: the days it runs, the colour it is drawn in, and the row of bars it
 * keeps to, so a job reads as one line across the days rather than hopping about.
 *
 * Shared by the month calendar and the week on Home, which work it out the same way and so draw
 * the same job in the same colour.
 */
data class CalendarProject(
    val id: Long,
    val name: String,
    val start: LocalDate,
    val end: LocalDate,
    /** Which of the calendar's colours, the same everywhere for the same project. */
    val colour: Int,
    val lane: Int,
    val status: ProjectStatus,
)

/** A project's bar through one day, rounded off on the days it starts and ends. */
data class DayBar(val colour: Int, val startsHere: Boolean, val endsHere: Boolean, val finished: Boolean)

fun CalendarProject.runsOn(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

/** Rows of bars under a day before they would crowd the date out. The rest are in the list. */
private const val MaxLanes = 3

/**
 * The projects with a day between [from] and [to], each given a row of bars to keep to.
 *
 * A project with only one of its dates filled in is shown on that day alone; one with neither
 * has nothing to put on a calendar. The rows are handed out first come, first served — a project
 * takes the first row free by the day it starts — so two jobs that overlap sit one above the
 * other instead of on top of each other.
 */
fun projectSpans(projects: List<ProjectEntity>, from: LocalDate, to: LocalDate): List<CalendarProject> {
    val dated = projects.mapNotNull { project ->
        val first = project.startDate ?: project.targetFinishDate ?: return@mapNotNull null
        val last = project.targetFinishDate ?: first
        // A finish typed before the start is read the other way round rather than dropped.
        if (last.isBefore(first)) Triple(project, last, first) else Triple(project, first, last)
    }
    // By id, so a project keeps its colour from month to month, and from Home to the calendar,
    // whatever else starts.
    val colourOf = dated.map { it.first.id }.sorted()
        .withIndex()
        .associate { (index, id) -> id to index % ProjectColours.size }
    val laneEnds = mutableListOf<LocalDate>()
    return dated
        .filter { (_, first, last) -> !last.isBefore(from) && !first.isAfter(to) }
        .sortedWith(compareBy({ it.second }, { it.first.id }))
        .map { (project, first, last) ->
            var lane = laneEnds.indexOfFirst { it.isBefore(first) }
            if (lane < 0) {
                laneEnds += last
                lane = laneEnds.lastIndex
            } else {
                laneEnds[lane] = last
            }
            CalendarProject(
                id = project.id,
                name = project.name,
                start = first,
                end = last,
                colour = colourOf.getValue(project.id),
                lane = lane,
                status = project.status,
            )
        }
}

/**
 * The bars under one day: a slot for every row the [spans] use, null where no project runs that
 * day, so the days of one stretch all stand the same height and the bars line up across them.
 */
fun barsOn(date: LocalDate, spans: List<CalendarProject>): List<DayBar?> {
    val lanes = minOf(MaxLanes, (spans.maxOfOrNull { it.lane } ?: -1) + 1)
    return (0 until lanes).map { lane ->
        spans.firstOrNull { it.lane == lane && it.runsOn(date) }?.let {
            DayBar(
                colour = it.colour,
                startsHere = date == it.start,
                endsHere = date == it.end,
                finished = it.status == ProjectStatus.COMPLETED,
            )
        }
    }
}

/**
 * The colours a project's bar can be. Picked to tell apart side by side and to sit with the
 * brand's browns; a finished job is drawn faded, still there but out of the way.
 */
private val ProjectColours = listOf(
    Accent,
    Ok,
    Accent2,
    Color(0xFF4F6D8C),
    Danger,
    Color(0xFF7A5C8E),
)

fun projectColour(index: Int, finished: Boolean): Color {
    val colour = ProjectColours[index.mod(ProjectColours.size)]
    return if (finished) colour.copy(alpha = 0.35f) else colour
}

/**
 * The bars under a day, full width so they run on into the next day's and read as one line
 * across the week. The cell they sit in must have no side padding of its own.
 */
@Composable
fun ProjectBars(bars: List<DayBar?>) {
    bars.forEach { bar ->
        if (bar == null) {
            Spacer(modifier = Modifier.padding(top = 2.dp).height(5.dp))
        } else {
            val round = 3.dp
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .padding(start = if (bar.startsHere) 4.dp else 0.dp, end = if (bar.endsHere) 4.dp else 0.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(
                        projectColour(bar.colour, bar.finished),
                        RoundedCornerShape(
                            topStart = if (bar.startsHere) round else 0.dp,
                            bottomStart = if (bar.startsHere) round else 0.dp,
                            topEnd = if (bar.endsHere) round else 0.dp,
                            bottomEnd = if (bar.endsHere) round else 0.dp,
                        ),
                    ),
            )
        }
    }
}

/** Which colour is which job, and when it runs — a line each, opening the project when tapped. */
@Composable
fun ProjectLegend(projects: List<CalendarProject>, onOpen: (Long) -> Unit) {
    Column {
        projects.forEach { project ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ChipShape)
                    .clickable { onOpen(project.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp, end = 10.dp)
                        .size(width = 18.dp, height = 6.dp)
                        .background(projectColour(project.colour, project.status == ProjectStatus.COMPLETED), ChipShape),
                )
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDateRange(project.start, project.end),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
