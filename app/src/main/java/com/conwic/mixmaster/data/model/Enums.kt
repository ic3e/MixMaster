package com.conwic.mixmaster.data.model

enum class Role {
    EMPLOYER,
    WORKER,
}

enum class ProjectStatus {
    ACTIVE,
    PLANNING,
    ON_HOLD,
    COMPLETED,
}

/** How a product's typical dose is expressed. */
enum class DosingMode {
    /** Fixed dose per m² per coat (e.g. microtoppings). */
    COATS,
    /** Fixed dose per m² for a single pour (e.g. self-levelling screeds dosed as one batch). */
    POUR,
    /** Dose per m² per millimetre of thickness (e.g. self-levelling compounds by depth). */
    MM,
}

enum class TaskPriority {
    HIGH,
    MEDIUM,
    LOW,
    DONE,
}
