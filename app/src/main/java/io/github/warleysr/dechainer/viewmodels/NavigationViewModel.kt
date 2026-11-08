package io.github.warleysr.dechainer.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import io.github.warleysr.dechainer.models.AppItem

/** Screen stack behind [io.github.warleysr.dechainer.activities.MainActivity]'s single-Activity routing. */
class NavigationViewModel : ViewModel() {
    private val stack = mutableStateListOf("restrictions")
    var selectedAppForRestrictions: AppItem? = null

    fun selectedTab() = stack.lastOrNull() ?: "restrictions"

    fun navigateTo(screen: String) {
        if (screen in listOf("restrictions", "apps", "config")) {
            stack.clear()
        }
        stack.add(screen)
    }

    fun navigateToAppRestrictions(app: AppItem) {
        selectedAppForRestrictions = app
        navigateTo("app_restrictions")
    }

    fun goBack(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            return true
        }
        return false
    }
}
