package com.lp.lessonplanner.viewmodel

import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.lp.lessonplanner.data.local.SavedPlanEntity
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── History CRUD ──────────────────────────────────────────────────────────────

fun LessonPlanViewModel.selectHistoryPlan(plan: SavedPlanEntity?) {
    _selectedHistoryPlan.value = plan
    _selectedHistoryPlans.value = if (plan != null) listOf(plan) else emptyList()
}

fun LessonPlanViewModel.selectHistoryPlans(plans: List<SavedPlanEntity>) {
    _selectedHistoryPlans.value = plans
    _selectedHistoryPlan.value = plans.firstOrNull()
}

fun LessonPlanViewModel.updateSelectedHistoryPlan(index: Int, updater: (SavedPlanEntity) -> SavedPlanEntity) {
    _selectedHistoryPlans.update { list ->
        list.mapIndexed { i, plan -> if (i == index) updater(plan) else plan }
    }
    if (index == 0) _selectedHistoryPlan.value = _selectedHistoryPlans.value.firstOrNull()
}

fun LessonPlanViewModel.moveSelectedHistoryPlan(index: Int, direction: Int) {
    _selectedHistoryPlans.update { list ->
        val newList = list.toMutableList()
        val newIndex = index + direction
        if (newIndex in newList.indices) {
            val temp = newList[index]
            newList[index] = newList[newIndex]
            newList[newIndex] = temp
        }
        newList
    }
    if (index == 0 || index + direction == 0) {
        _selectedHistoryPlan.value = _selectedHistoryPlans.value.firstOrNull()
    }
}

fun LessonPlanViewModel.deletePlan(plan: SavedPlanEntity) {
    viewModelScope.launch {
        repository.deletePlan(plan)
        _historyOrderIds.value = _historyOrderIds.value.filter { it != plan.id }
        repository.setSetting("history_order_ids", _historyOrderIds.value.joinToString(","))
        if (_selectedHistoryPlan.value?.id == plan.id) _selectedHistoryPlan.value = null
        _selectedHistoryPlans.update { list -> list.filter { it.id != plan.id } }
        loadHistoryPaginated(_uiState.value.historyPage)
    }
}

fun LessonPlanViewModel.deleteMultiplePlans(plans: List<SavedPlanEntity>) {
    viewModelScope.launch {
        val idsToDelete = plans.map { it.id }.toSet()
        plans.forEach { repository.deletePlan(it) }
        _historyOrderIds.value = _historyOrderIds.value.filter { it !in idsToDelete }
        repository.setSetting("history_order_ids", _historyOrderIds.value.joinToString(","))
        if (_selectedHistoryPlan.value?.id in idsToDelete) _selectedHistoryPlan.value = null
        _selectedHistoryPlans.update { list -> list.filter { it.id !in idsToDelete } }
        loadHistoryPaginated(_uiState.value.historyPage)
    }
}

fun LessonPlanViewModel.duplicatePlan(plan: SavedPlanEntity) {
    viewModelScope.launch {
        val duplicate = plan.copy(id = 0, createdAt = System.currentTimeMillis())
        val newId = repository.savePlan(duplicate).toInt()
        val savedDuplicate = duplicate.copy(id = newId)

        val currentOrder = if (_historyOrderIds.value.isEmpty()) {
            history.value.map { it.id }.toMutableList()
        } else {
            _historyOrderIds.value.toMutableList()
        }

        val index = currentOrder.indexOf(plan.id)
        if (index != -1) currentOrder.add(index + 1, newId) else currentOrder.add(0, newId)
        updateHistoryOrder(currentOrder)
        
        _selectedHistoryPlans.update { list ->
            val newList = list.toMutableList()
            val listIndex = list.indexOfFirst { it.id == plan.id }
            if (listIndex != -1) newList.add(listIndex + 1, savedDuplicate) else newList.add(savedDuplicate)
            newList
        }
        
        loadHistoryPaginated(_uiState.value.historyPage)
    }
}

fun LessonPlanViewModel.saveEditedHistoryPlan() {
    viewModelScope.launch {
        val plans = _selectedHistoryPlans.value
        if (plans.isNotEmpty()) {
            plans.forEach { plan -> repository.updatePlan(plan) }
            _saveSuccess.value = true
            loadHistoryPaginated(_uiState.value.historyPage)
        }
    }
}

// ── History ordering & pagination ─────────────────────────────────────────────

fun LessonPlanViewModel.updateHistoryOrder(orderedIds: List<Int>) {
    _historyOrderIds.value = orderedIds
    viewModelScope.launch {
        repository.setSetting("history_order_ids", orderedIds.joinToString(","))
    }
}

fun LessonPlanViewModel.updateHistoryPage(page: Int) {
    _uiState.update { it.copy(historyPage = page) }
    loadHistoryPaginated(page)
}

fun LessonPlanViewModel.updateHistorySearch(query: String) {
    _uiState.update { it.copy(historySearchQuery = query, historyPage = 0) }
    loadHistoryPaginated(0)
}

fun LessonPlanViewModel.updateHistoryTab(index: Int) {
    _uiState.update { it.copy(historyTabIndex = index, historyPage = 0) }
    loadHistoryPaginated(0)
}

fun LessonPlanViewModel.loadHistoryPaginated(page: Int) {
    viewModelScope.launch {
        val limit = 10
        val offset = page * limit
        val state = _uiState.value
        
        val planType = when (state.historyTabIndex) {
            0 -> "Lesson Plan"
            1 -> "Full Note"
            2 -> "Questions"
            else -> null
        }
        
        val plans = if (state.historySearchQuery.isEmpty() && planType == null) {
            repository.getSavedPlansPaginated(limit, offset)
        } else {
            repository.getFilteredSavedPlansPaginated(state.historySearchQuery, planType, limit, offset)
        }

        val total = if (state.historySearchQuery.isEmpty() && planType == null) {
            repository.getSavedPlanCount()
        } else {
            repository.getFilteredSavedPlanCount(state.historySearchQuery, planType)
        }

        _uiState.update {
            it.copy(paginatedHistory = plans, totalHistoryCount = total, historyPage = page)
        }
    }
}

// ── Generated plan management ─────────────────────────────────────────────────

fun LessonPlanViewModel.deleteGeneratedPlan(planIndex: Int) {
    _uiState.update { state ->
        val newJsons = state.generatedPlanJsons.toMutableList().also {
            if (planIndex in it.indices) it.removeAt(planIndex)
        }
        val newIndicators = state.selectedIndicatorIds.toMutableList().also {
            if (planIndex in it.indices) it.removeAt(planIndex)
        }
        state.copy(generatedPlanJsons = newJsons, selectedIndicatorIds = newIndicators)
    }
}

fun LessonPlanViewModel.duplicateGeneratedPlan(planIndex: Int) {
    _uiState.update { state ->
        val newJsons = state.generatedPlanJsons.toMutableList().also {
            if (planIndex in it.indices) it.add(planIndex + 1, it[planIndex])
        }
        val newIndicators = state.selectedIndicatorIds.toMutableList().also {
            if (planIndex in it.indices) it.add(planIndex + 1, it[planIndex])
        }
        state.copy(generatedPlanJsons = newJsons, selectedIndicatorIds = newIndicators)
    }
}

fun LessonPlanViewModel.moveGeneratedPlan(planIndex: Int, direction: Int) {
    _uiState.update { state ->
        val newJsons = state.generatedPlanJsons.toMutableList()
        val newIndicators = state.selectedIndicatorIds.toMutableList()
        val target = planIndex + direction
        if (target in newJsons.indices) {
            newJsons[planIndex] = newJsons[target].also { newJsons[target] = newJsons[planIndex] }
        }
        if (target in newIndicators.indices) {
            newIndicators[planIndex] = newIndicators[target].also { newIndicators[target] = newIndicators[planIndex] }
        }
        state.copy(generatedPlanJsons = newJsons, selectedIndicatorIds = newIndicators)
    }
}

fun LessonPlanViewModel.savePlans() {
    viewModelScope.launch {
        val state = _uiState.value
        val contents = state.generatedPlanJsons
        if (contents.isEmpty()) return@launch

        contents.forEach { content ->
            var indicatorCodeStr = ""
            var weekStr = ""
            var dateStr = ""
            var lessonStr = ""

            try {
                val planType = content.detectPlanType()
                val header = content.parsePlanHeader(gson, planType)
                indicatorCodeStr = content.extractIndicatorCodeFromRaw().ifBlank {
                    header?.extractIndicatorCode() ?: ""
                }
                header?.week?.takeIf { it.isNotBlank() }?.let { weekStr = it }
                header?.date?.takeIf { it.isNotBlank() }?.let { dateStr = it }
                header?.lesson?.takeIf { it.isNotBlank() }?.let { lessonStr = it }
            } catch (_: Exception) { }

            repository.savePlan(
                SavedPlanEntity(
                    date = dateStr,
                    week = weekStr,
                    lessonNumber = lessonStr,
                    indicatorCode = indicatorCodeStr,
                    content = content,
                    planType = state.generationType
                )
            )
        }
        _saveSuccess.value = true
        loadHistoryPaginated(0)
    }
}
