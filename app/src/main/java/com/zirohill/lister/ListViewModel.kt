package com.zirohill.lister

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.collections.forEach

data class Category(val id: Long, val name: String, val position: Int = 0)
data class TodoItem(val id: Long, val task: String, val isDone: Boolean, val categoryId: Long,
                    val order: Int)

class TodoViewModel(private val dbHelper: DbHelper) : ViewModel() {
    private val _todos = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: StateFlow<List<TodoItem>> = _todos

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var lastClearedCompleted: List<TodoItem>? = null
    private var undoJob: Job? = null

    init {
        loadTodos()
    }

    private fun loadTodos() = viewModelScope.launch {
        _isLoading.value = true
        val categories = dbHelper.getCategories()
        val categoryById = categories.associateBy { it.id }

        val loaded = dbHelper.getTodos()
        _todos.value = loaded
        _isLoading.value = false
    }

    fun addTask(task: String, category: Category?, onError: (String) -> Unit) =
        viewModelScope.launch {
            if (category == null) {
                onError("Category must be selected")
                return@launch
            }
            try {
                dbHelper.addTodo(task, category)
                loadTodos()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to add task")
            }
        }

    fun deleteTask(id: Long) = viewModelScope.launch {
        dbHelper.deleteTodo(id)
        loadTodos()
    }

    fun restoreClearedCompleted() {
        undoJob?.cancel()
        val restoreList = lastClearedCompleted
        lastClearedCompleted = null
        if (restoreList.isNullOrEmpty()) return
        viewModelScope.launch {
            val db = dbHelper.writableDatabase
            restoreList.forEach { todo ->
                val values = ContentValues().apply { put(DbSchema.COL_DELETED, 0) }
                db.update(
                    DbSchema.TABLE_NAME,
                    values,
                    "${DbSchema.COL_ID} = ?",
                    arrayOf(todo.id.toString())
                )
            }
            loadTodos()
        }
    }

    fun clearCompletedByCategory(category: Category, onUndoWindowOver: () -> Unit) {
        undoJob?.cancel()
        viewModelScope.launch {
            val completedInCategory =
                _todos.value.filter { it.isDone && it.categoryId == category.id }
            if (completedInCategory.isEmpty()) {
                onUndoWindowOver()
                return@launch
            }
            lastClearedCompleted = completedInCategory
            dbHelper.clearCompletedTodosByCategory(category)
            loadTodos()

            undoJob = viewModelScope.launch {
                delay(5000L)
                lastClearedCompleted = null
                onUndoWindowOver()
            }
        }
    }

    fun hasCompletedTasksInCategory(category: Category): Boolean =
        _todos.value.any { it.isDone && it.categoryId == category.id }

    fun toggleDone(todo: TodoItem) = viewModelScope.launch {
        val newIsDone = !todo.isDone
        val newOrder = getMaxOrderInCategory(todo.categoryId, newIsDone) + 1
        val updatedTodo = todo.copy(isDone = newIsDone, order = newOrder)
        dbHelper.updateTodo(updatedTodo)
        loadTodos()
    }

    private suspend fun getMaxOrderInCategory(categoryId: Long, completed: Boolean): Int {
        val todos = dbHelper.getTodos()
        return todos.filter { it.categoryId == categoryId && it.isDone == completed }
            .maxOfOrNull { it.order } ?: 0
    }

    fun updateTask(todo: TodoItem) = viewModelScope.launch {
        dbHelper.updateTodo(todo)
        loadTodos()  // MUST re-load and resort to update UI ordering correctly
    }

    fun moveTaskUp(todo: TodoItem) = viewModelScope.launch {
        dbHelper.moveTaskUp(todo)
        loadTodos()
    }

    fun moveTaskDown(todo: TodoItem) = viewModelScope.launch {
        dbHelper.moveTaskDown(todo)
        loadTodos()
    }
}


class TodoViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TodoViewModel(DbHelper(context)) as T
}

class CategoryViewModel(private val dbHelper: DbHelper) : ViewModel() {
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    init {
        loadCategories()
    }

    fun loadCategories() = viewModelScope.launch {
        _categories.value = dbHelper.getCategories()
    }

    fun addCategory(name: String) = viewModelScope.launch {
        dbHelper.addCategory(name)
        loadCategories()
    }

    fun updateCategory(cat: Category) = viewModelScope.launch {
        dbHelper.updateCategory(cat)
        loadCategories()
    }

    fun deleteCategory(id: Long) = viewModelScope.launch {
        dbHelper.deleteCategory(id)
        loadCategories()
    }

    fun moveCategoryUp(category: Category) = viewModelScope.launch {
        try {
            dbHelper.moveCategoryUp(category)
            // Re-normalize positions to ensure order integrity
            dbHelper.normalizeCategoryPositions()
            loadCategories()
        } catch (e: Exception) {
            // handle error if needed
        }
    }

    fun moveCategoryDown(category: Category) = viewModelScope.launch {
        try {
            dbHelper.moveCategoryDown(category)
            // Re-normalize positions after move
            dbHelper.normalizeCategoryPositions()
            loadCategories()
        } catch (e: Exception) {
            // handle error if needed
        }
    }
}


class CategoryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CategoryViewModel(DbHelper(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
