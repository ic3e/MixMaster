package com.conwic.mixmaster.ui.guide

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.conwic.mixmaster.R

/**
 * One shot of the guide: what is said, and the picture that goes with it.
 *
 * The picture steps through [beats] moments — a field filling in, a finger going to the next
 * button — evenly over the time the caption is on screen. [picture] is handed the moment it is on.
 */
internal class GuideScene(
    @StringRes val caption: Int,
    val beats: Int,
    val picture: @Composable (beat: Int) -> Unit,
)

internal class GuideChapter(
    @StringRes val title: Int,
    val icon: ImageVector,
    val scenes: List<GuideScene>,
)

/**
 * The whole guide, in the order a new hand needs it: finding the way round, mixing, what the mixes
 * are made of, a job, the shed, the day's tasks, the crew, and the settings.
 *
 * Anything that changes how the app works — a new screen, a button moved or renamed — changes the
 * scene that shows it, in the same commit, the way Pour Day's How to play is kept true.
 */
internal val Guide: List<GuideChapter> = listOf(
    GuideChapter(
        R.string.guide_ch_around,
        Icons.Filled.Home,
        listOf(
            GuideScene(R.string.guide_around_1, 4) { beat ->
                HomeMock(spot = listOf(HomeSpot.None, HomeSpot.Warehouse, HomeSpot.Week, HomeSpot.Today)[beat])
            },
            GuideScene(R.string.guide_around_2, 6) { beat -> HomeMock(litTab = beat - 1) },
            GuideScene(R.string.guide_around_3, 4) { beat ->
                HomeMock(
                    spot = when (beat) {
                        1 -> HomeSpot.Tick
                        3 -> HomeSpot.AddTask
                        else -> HomeSpot.None
                    },
                    ticked = beat >= 2,
                )
            },
        ),
    ),
    GuideChapter(
        R.string.guide_ch_mixing,
        Icons.Filled.Science,
        listOf(
            GuideScene(R.string.guide_mixing_1, 2) { beat -> HomeMock(spot = if (beat >= 1) HomeSpot.Calc else HomeSpot.None) },
            GuideScene(R.string.guide_mixing_2, 4) { beat ->
                CalcMock(
                    chosen = beat >= 1,
                    area = listOf("", "", "42", "42.5")[beat],
                    parts = 0,
                    mixing = false,
                    spot = when (beat) {
                        0 -> CalcSpot.Product
                        3 -> CalcSpot.None
                        else -> CalcSpot.Area
                    },
                )
            },
            GuideScene(R.string.guide_mixing_3, 4) { beat -> CalcMock(chosen = true, area = "42.5", parts = beat + 1, mixing = false) },
            GuideScene(R.string.guide_mixing_4, 3) { beat ->
                CalcMock(chosen = true, area = "42.5", parts = 0, mixing = beat >= 1, spot = if (beat == 2) CalcSpot.Mixer else CalcSpot.None)
            },
            GuideScene(R.string.guide_mixing_5, 4) { beat ->
                when (beat) {
                    0 -> CalcMock(chosen = true, area = "42.5", parts = 0, mixing = true, spot = CalcSpot.Start)
                    1 -> MixingMock(batch = 1, of = 4, ring = 0f, state = MixState.Loading, tapButton = true)
                    2 -> MixingMock(batch = 1, of = 4, ring = 0.4f, state = MixState.Running)
                    else -> MixingMock(batch = 1, of = 4, ring = 0.8f, state = MixState.Running)
                }
            },
            GuideScene(R.string.guide_mixing_6, 4) { beat ->
                when (beat) {
                    0 -> MixingMock(batch = 1, of = 4, ring = 1f, state = MixState.Ready)
                    1 -> MixingMock(batch = 1, of = 4, ring = 1f, state = MixState.Ready, tapButton = true)
                    2 -> MixingMock(batch = 2, of = 4, ring = 0f, state = MixState.Loading)
                    else -> MixingMock(batch = 4, of = 4, ring = 1f, state = MixState.Ready)
                }
            },
            GuideScene(R.string.guide_mixing_7, 3) { beat -> SummaryMock(recorded = beat >= 2, tapRecord = beat == 1) },
        ),
    ),
    GuideChapter(
        R.string.guide_ch_products,
        Icons.Filled.Inventory2,
        listOf(
            GuideScene(R.string.guide_products_1, 3) { beat -> ProductsMock(tab = 0, litRow = beat - 1) },
            GuideScene(R.string.guide_products_2, 3) { beat ->
                if (beat == 0) ProductsMock(tab = 0, tapRow = 0) else ProductMock(tapSheet = beat == 2)
            },
            GuideScene(R.string.guide_products_3, 3) { beat -> ProductsMock(tab = 1, litRow = beat - 1) },
            GuideScene(R.string.guide_products_4, 3) { beat ->
                RecipeMock(coat = if (beat >= 2) 1 else 0, colour = false, tapCoat = if (beat == 1) 1 else -1)
            },
            GuideScene(R.string.guide_products_5, 3) { beat -> RecipeMock(coat = 0, colour = beat >= 1) },
        ),
    ),
    GuideChapter(
        R.string.guide_ch_projects,
        Icons.Filled.CalendarMonth,
        listOf(
            GuideScene(R.string.guide_projects_1, 5) { beat -> NewProjectMock(filled = beat, tapSave = beat == 4) },
            GuideScene(R.string.guide_projects_2, 4) { beat ->
                LayoutMock(
                    rooms = listOf(0, 1, 1, 2)[beat],
                    coats = 0,
                    colour = false,
                    spot = if (beat == 0 || beat == 2) LayoutSpot.AddRoom else LayoutSpot.None,
                )
            },
            GuideScene(R.string.guide_projects_3, 6) { beat ->
                LayoutMock(rooms = 2, coats = beat + 1, colour = false, spot = if (beat < 5) LayoutSpot.AddCoat else LayoutSpot.None)
            },
            GuideScene(R.string.guide_projects_4, 4) { beat ->
                LayoutMock(
                    rooms = 2,
                    coats = 6,
                    colour = beat >= 2,
                    spot = listOf(LayoutSpot.Colour, LayoutSpot.Colour, LayoutSpot.None, LayoutSpot.Mix)[beat],
                )
            },
            GuideScene(R.string.guide_projects_5, 4) { beat -> MaterialsMock(lines = beat + 2, tapPickup = beat == 3) },
            GuideScene(R.string.guide_projects_6, 4) { beat ->
                JobNotesMock(spot = listOf(NotesSpot.Blueprints, NotesSpot.Photos, NotesSpot.Note, NotesSpot.Report)[beat])
            },
        ),
    ),
    GuideChapter(
        R.string.guide_ch_warehouse,
        Icons.Filled.Warehouse,
        listOf(
            GuideScene(R.string.guide_warehouse_1, 3) { beat -> WarehouseMock(spot = if (beat >= 1) WhSpot.Shelf else WhSpot.None) },
            GuideScene(R.string.guide_warehouse_2, 3) { beat ->
                if (beat == 0) WarehouseMock(spot = WhSpot.ToOrder) else HomeMock(spot = HomeSpot.Warehouse)
            },
            GuideScene(R.string.guide_warehouse_3, 5) { beat ->
                if (beat == 0) WarehouseMock(spot = WhSpot.TapLine) else OrderMock(filled = minOf(beat, 3), tapSave = beat == 4)
            },
            GuideScene(R.string.guide_warehouse_4, 3) { beat -> ArrivedMock(asked = beat >= 1, arrived = beat >= 2) },
            GuideScene(R.string.guide_warehouse_5, 4) { beat ->
                CountMock(packs = if (beat >= 2) 5 else 4, counted = if (beat >= 3) 4 else 3, tapMore = beat == 1)
            },
        ),
    ),
    GuideChapter(
        R.string.guide_ch_tasks,
        Icons.Filled.DateRange,
        listOf(
            GuideScene(R.string.guide_tasks_1, 5) { beat -> TaskMock(filled = beat, tapSave = beat == 4) },
            GuideScene(R.string.guide_tasks_2, 3) { beat -> HomeMock(spot = if (beat == 2) HomeSpot.Today else HomeSpot.Week) },
            GuideScene(R.string.guide_tasks_3, 3) { beat -> JobNotesMock(spot = if (beat == 2) NotesSpot.Photos else NotesSpot.Note) },
        ),
    ),
    GuideChapter(
        R.string.guide_ch_company,
        Icons.Filled.Groups,
        listOf(
            GuideScene(R.string.guide_company_1, 4) { beat -> SyncMock(step = beat) },
            GuideScene(R.string.guide_company_2, 5) { beat -> PersonMock(perms = beat, codeShown = false, tapShare = false) },
            GuideScene(R.string.guide_company_3, 4) { beat ->
                when (beat) {
                    0 -> PersonMock(perms = 4, codeShown = true, tapShare = false)
                    1 -> PersonMock(perms = 4, codeShown = true, tapShare = true)
                    2 -> JoinMock(tapJoin = false)
                    else -> JoinMock(tapJoin = true)
                }
            },
            GuideScene(R.string.guide_company_4, 3) { beat -> RemoveMock(step = beat) },
        ),
    ),
    GuideChapter(
        R.string.guide_ch_settings,
        Icons.Filled.Settings,
        listOf(
            GuideScene(R.string.guide_settings_1, 2) { beat -> SettingsMock(if (beat >= 1) SettingsSpot.Backup else SettingsSpot.None) },
            GuideScene(R.string.guide_settings_2, 2) { beat -> SettingsMock(if (beat >= 1) SettingsSpot.Lock else SettingsSpot.None) },
            GuideScene(R.string.guide_settings_3, 3) { beat -> SettingsMock(if (beat == 2) SettingsSpot.Demo else SettingsSpot.Look) },
            GuideScene(R.string.guide_settings_4, 4) { beat ->
                if (beat == 0) SettingsMock(SettingsSpot.Empty) else EmptyMock(stage = beat - 1 + if (beat == 3) 1 else 0)
            },
            GuideScene(R.string.guide_settings_5, 2) { PourDayMock() },
        ),
    ),
)
