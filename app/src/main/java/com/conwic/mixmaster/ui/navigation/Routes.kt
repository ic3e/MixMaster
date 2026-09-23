package com.conwic.mixmaster.ui.navigation

object Routes {
    const val SIGN_IN = "sign_in"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    /**
     * The calculator, optionally opened for one product.
     *
     * Which product is carried by the navigation itself. It used to be left in a preference
     * and picked up on the other side, which raced: the screen came up on whatever was stored,
     * then jumped to the product actually asked for a beat later.
     */
    const val CALCULATOR = "calculator?productId={productId}"
    const val CALCULATOR_PRODUCT = "productId"
    const val PRODUCTS = "products"
    const val PRODUCT_DETAIL = "product/{productId}"
    const val PRODUCT_ADD = "product/add"
    const val PRODUCT_EDIT = "product/edit/{productId}"
    const val PROJECTS = "projects"
    const val PROJECT_DETAIL = "project/{projectId}"
    const val CALENDAR = "calendar"
    const val WAREHOUSE = "warehouse"
    const val SETTINGS = "settings"

    fun calculator(productId: Long = 0L): String =
        if (productId > 0L) "calculator?productId=$productId" else "calculator"

    fun productDetail(id: Long) = "product/$id"
    fun productEdit(id: Long) = "product/edit/$id"
    fun projectDetail(id: Long) = "project/$id"

    /**
     * Bottom-nav top-level destinations, in display order.
     *
     * The calculator is not among them any more: it belongs to a product, and is opened from
     * one. Its slot went to the warehouse, which is the thing you check before a job.
     */
    val bottomNavRoutes = listOf(HOME, WAREHOUSE, PRODUCTS, PROJECTS, SETTINGS)
}
