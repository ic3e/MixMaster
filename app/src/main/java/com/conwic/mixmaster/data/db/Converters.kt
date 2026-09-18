package com.conwic.mixmaster.data.db

import androidx.room.TypeConverter
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.model.TaskPriority
import java.time.Instant
import java.time.LocalDate

class Converters {

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun roleToString(value: Role?): String? = value?.name

    @TypeConverter
    fun stringToRole(value: String?): Role? = value?.let(Role::valueOf)

    @TypeConverter
    fun statusToString(value: ProjectStatus?): String? = value?.name

    @TypeConverter
    fun stringToStatus(value: String?): ProjectStatus? = value?.let(ProjectStatus::valueOf)

    @TypeConverter
    fun dosingModeToString(value: DosingMode?): String? = value?.name

    @TypeConverter
    fun stringToDosingMode(value: String?): DosingMode? = value?.let(DosingMode::valueOf)

    @TypeConverter
    fun priorityToString(value: TaskPriority?): String? = value?.name

    @TypeConverter
    fun stringToPriority(value: String?): TaskPriority? = value?.let(TaskPriority::valueOf)
}
