package com.safemode.hbconnect.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safemode.hbconnect.Graph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the user's custom order for the reorderable "More" entries and persists changes. */
class MoreViewModel : ViewModel() {
    private val settings = Graph.settingsRepository

    /** The saved custom order as route ids; empty means the default alphabetical order. */
    val customOrder: StateFlow<List<String>> = settings.config
        .map { it.moreOrder }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Persists an explicit order of route ids for the reorderable section. */
    fun saveOrder(order: List<String>) {
        viewModelScope.launch {
            val config = settings.config.first()
            settings.save(config.copy(moreOrder = order))
        }
    }

    /** Clears the custom order, returning the section to alphabetical. */
    fun resetOrder() = saveOrder(emptyList())
}
