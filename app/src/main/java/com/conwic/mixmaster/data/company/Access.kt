package com.conwic.mixmaster.data.company

import com.conwic.mixmaster.data.model.Role

/**
 * What this phone may change, for the screens to decide which buttons to show.
 *
 * In a company it is whatever the employer set for this person, and the server holds to it
 * whatever the screens show. On a phone of its own it follows the old employer/worker switch,
 * exactly as before there were companies.
 */
data class Access(
    val catalogue: Boolean,
    val projects: Boolean,
    val warehouse: Boolean,
    val site: Boolean,
    /** Runs the company: adds people and sets what they may do. */
    val owner: Boolean,
    val inCompany: Boolean,
) {
    /** How notes are signed, and which help text a screen shows. */
    val role: Role get() = if (owner) Role.EMPLOYER else Role.WORKER

    companion object {
        fun of(role: Role, link: CompanyLink?): Access = when {
            link != null -> Access(
                catalogue = link.owner || link.perms.catalogue,
                projects = link.owner || link.perms.projects,
                warehouse = link.owner || link.perms.warehouse,
                site = link.owner || link.perms.site,
                owner = link.owner,
                inCompany = true,
            )
            role == Role.EMPLOYER -> Access(
                catalogue = true, projects = true, warehouse = true, site = true, owner = true, inCompany = false,
            )
            else -> Access(
                catalogue = false, projects = false, warehouse = true, site = true, owner = false, inCompany = false,
            )
        }
    }
}
