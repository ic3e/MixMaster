package com.conwic.mixmaster.ui.navigation

object Routes {
    const val SIGN_IN = "sign_in"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CALCULATOR = "calculator"
    const val PRODUCTS = "products"
    const val PRODUCT_DETAIL = "product/{productId}"
    const val PRODUCT_ADD = "product/add"
    const val PRODUCT_EDIT = "product/edit/{productId}"
    const val PROJECTS = "projects"
    const val PROJECT_DETAIL = "project/{projectId}"
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"

    fun productDetail(id: Long) = "product/$id"
    fun productEdit(id: Long) = "product/edit/$id"
    fun projectDetail(id: Long) = "project/$id"

    /** Bottom-nav top-level destinations, in display order. */
    val bottomNavRoutes = listOf(HOME, CALCULATOR, PRODUCTS, PROJECTS, SETTINGS)
}
