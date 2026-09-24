package com.conwic.mixmaster.ui.navigation

import android.net.Uri

object Routes {
    const val SIGN_IN = "sign_in"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    /**
     * The calculator, optionally opened for one mix.
     *
     * Which mix is carried by the navigation itself. It used to be left in a preference and
     * picked up on the other side, which raced: the screen came up on whatever was stored, then
     * jumped to the one actually asked for a beat later.
     */
    const val CALCULATOR = "calculator?solutionId={solutionId}&area={area}&dose={dose}" +
        "&coats={coats}&job={job}&project={project}&room={room}&layer={layer}"
    const val CALCULATOR_SOLUTION = "solutionId"
    const val CALCULATOR_AREA = "area"
    const val CALCULATOR_DOSE = "dose"
    const val CALCULATOR_COATS = "coats"
    const val CALCULATOR_JOB = "job"
    const val CALCULATOR_PROJECT = "project"
    const val CALCULATOR_ROOM = "room"
    const val CALCULATOR_LAYER = "layer"
    const val PRODUCTS = "products"
    const val PRODUCT_DETAIL = "product/{productId}"
    const val PRODUCT_ADD = "product/add"
    const val PRODUCT_EDIT = "product/edit/{productId}"
    const val SOLUTION_ADD = "solution/add"
    const val SOLUTION_EDIT = "solution/edit/{solutionId}"
    const val PROJECTS = "projects"
    const val PROJECT_DETAIL = "project/{projectId}"
    const val CALENDAR = "calendar"
    const val WAREHOUSE = "warehouse"
    const val SETTINGS = "settings"

    fun calculator(solutionId: Long = 0L): String =
        if (solutionId > 0L) "calculator?solutionId=$solutionId" else "calculator"

    /**
     * The calculator opened for one coat of one room.
     *
     * The room already knows its area, the coat already knows the rate it goes on at and how
     * many passes it takes — retyping all three into the calculator is how the figures drift
     * apart. They travel in the route rather than in a preference so that going back and
     * opening a different coat can't leave the screen showing the last one's numbers.
     */
    fun calculatorForCoat(
        solutionId: Long,
        areaM2: Double,
        doseGramsPerM2: Double,
        quantity: Double,
        jobLabel: String,
        projectId: Long,
        roomId: Long,
        /** The coat's own row, which is where its colour is kept. */
        layerId: Long,
    ): String = "calculator?solutionId=$solutionId" +
        "&area=$areaM2&dose=$doseGramsPerM2&coats=$quantity&job=${Uri.encode(jobLabel)}" +
        "&project=$projectId&room=$roomId&layer=$layerId"

    fun productDetail(id: Long) = "product/$id"
    fun productEdit(id: Long) = "product/edit/$id"

    fun solutionEdit(id: Long) = "solution/edit/$id"
    fun projectDetail(id: Long) = "project/$id"

    /**
     * Bottom-nav top-level destinations, in display order.
     *
     * The calculator is not among them any more: it belongs to a product, and is opened from
     * one. Its slot went to the warehouse, which is the thing you check before a job.
     */
    val bottomNavRoutes = listOf(HOME, WAREHOUSE, PRODUCTS, PROJECTS, SETTINGS)
}
