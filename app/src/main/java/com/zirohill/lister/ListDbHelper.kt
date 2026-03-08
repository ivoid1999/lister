package com.zirohill.lister

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.SharedPreferences

object DbSchema {
    const val TABLE_NAME = "todo_items"
    const val COL_ID = "id"
    const val COL_TASK = "task"
    const val COL_DONE = "isDone"
    const val COL_DELETED = "isDeleted"
    const val COL_CATEGORY = "category"
}

object ChainSchema {
    const val TABLE_NAME = "chain_entries"
    const val COL_DATE = "date"
    const val COL_DONE = "isDone"
}

object PreferencesManager {
    const val FILE = "app_prefs"
    const val HOUR = "reminder_hour"
    const val MINUTE = "reminder_minute"
    const val REMINDER_ENABLED = "reminder_enabled"
    const val LAST_PAGE = "last_opened_page"
    const val HABIT_ENABLED = "habit_enabled"
    private const val PREF_FILE = FILE
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        }
    }

    val sharedPrefs get() = prefs

    fun saveLastPage(pageRoute: String) {
        prefs.edit().putString(LAST_PAGE, pageRoute).apply()
    }

    fun getLastPage(defaultPage: String = "labelList"): String =
        prefs.getString(LAST_PAGE, defaultPage) ?: defaultPage

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(REMINDER_ENABLED, enabled).apply()
    }

    fun isReminderEnabled(default: Boolean = true): Boolean =
        prefs.getBoolean(REMINDER_ENABLED, default)

    fun setHabitEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(HABIT_ENABLED, enabled).apply()
    }

    fun isHabitEnabled(default: Boolean = false): Boolean =
        prefs.getBoolean(HABIT_ENABLED, default)
}

class DbHelper(context: Context) : SQLiteOpenHelper(context, "todo.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                position INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE ${DbSchema.TABLE_NAME} (
                ${DbSchema.COL_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DbSchema.COL_TASK} TEXT NOT NULL,
                ${DbSchema.COL_DONE} INTEGER NOT NULL DEFAULT 0,
                ${DbSchema.COL_DELETED} INTEGER NOT NULL DEFAULT 0,
                ${DbSchema.COL_CATEGORY} INTEGER NOT NULL,
                order_in_category INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(${DbSchema.COL_CATEGORY}) REFERENCES categories(id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE ${ChainSchema.TABLE_NAME} (
                ${ChainSchema.COL_DATE} TEXT PRIMARY KEY,
                ${ChainSchema.COL_DONE} INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE user_goal (
                id INTEGER PRIMARY KEY CHECK(id = 1),
                goal_text TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE ${DbSchema.TABLE_NAME} ADD COLUMN ${DbSchema.COL_DELETED} INTEGER DEFAULT 0")
        }
        // For any future upgrades, add corresponding logic here
    }

    suspend fun getTodos(): List<TodoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<TodoItem>()
        readableDatabase.query(
            DbSchema.TABLE_NAME,
            null,
            "${DbSchema.COL_DELETED} = 0",
            null,
            null,
            null,
            "${DbSchema.COL_DONE} ASC, order_in_category ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    TodoItem(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(DbSchema.COL_ID)),
                        task = cursor.getString(cursor.getColumnIndexOrThrow(DbSchema.COL_TASK)),
                        isDone = cursor.getInt(cursor.getColumnIndexOrThrow(DbSchema.COL_DONE)) == 1,
                        categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(DbSchema.COL_CATEGORY)),
                        order = cursor.getInt(cursor.getColumnIndexOrThrow("order_in_category"))
                    )
                )
            }
        }
        result
    }

    suspend fun getMaxOrderInCategory(categoryId: Long): Int = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT MAX(order_in_category) FROM ${DbSchema.TABLE_NAME} WHERE ${DbSchema.COL_CATEGORY} = ? AND ${DbSchema.COL_DELETED} = 0",
            arrayOf(categoryId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    suspend fun addTodo(task: String, category: Category) = withContext(Dispatchers.IO) {
        val nextOrder = getMaxOrderInCategory(category.id) + 1
        writableDatabase.insert(DbSchema.TABLE_NAME, null, ContentValues().apply {
            put(DbSchema.COL_TASK, task)
            put(DbSchema.COL_DONE, 0)
            put(DbSchema.COL_DELETED, 0)
            put(DbSchema.COL_CATEGORY, category.id)
            put("order_in_category", nextOrder)
        })
    }

    suspend fun updateTodo(todo: TodoItem) = withContext(Dispatchers.IO) {
        writableDatabase.update(DbSchema.TABLE_NAME, ContentValues().apply {
            put(DbSchema.COL_TASK, todo.task)
            put(DbSchema.COL_DONE, if (todo.isDone) 1 else 0)
            put(DbSchema.COL_CATEGORY, todo.categoryId)
            put("order_in_category", todo.order)
        }, "${DbSchema.COL_ID} = ?", arrayOf(todo.id.toString()))
    }

    suspend fun updateTaskOrder(taskId: Long, newOrder: Int) = withContext(Dispatchers.IO) {
        writableDatabase.update(
            DbSchema.TABLE_NAME,
            ContentValues().apply { put("order_in_category", newOrder) },
            "${DbSchema.COL_ID} = ?",
            arrayOf(taskId.toString())
        )
    }

    suspend fun moveTaskUp(todo: TodoItem) = withContext(Dispatchers.IO) {
        val cursor = readableDatabase.query(
            DbSchema.TABLE_NAME,
            null,
            "${DbSchema.COL_CATEGORY} = ? AND order_in_category < ? AND ${DbSchema.COL_DELETED} = 0",
            arrayOf(todo.categoryId.toString(), todo.order.toString()),
            null,
            null,
            "order_in_category DESC",
            "1"
        )
        if (cursor.moveToFirst()) {
            val aboveId = cursor.getLong(cursor.getColumnIndexOrThrow(DbSchema.COL_ID))
            val aboveOrder = cursor.getInt(cursor.getColumnIndexOrThrow("order_in_category"))
            updateTaskOrder(todo.id, aboveOrder)
            updateTaskOrder(aboveId, todo.order)
        }
        cursor.close()
    }

    suspend fun moveTaskDown(todo: TodoItem) = withContext(Dispatchers.IO) {
        val cursor = readableDatabase.query(
            DbSchema.TABLE_NAME,
            null,
            "${DbSchema.COL_CATEGORY} = ? AND order_in_category > ? AND ${DbSchema.COL_DELETED} = 0",
            arrayOf(todo.categoryId.toString(), todo.order.toString()),
            null,
            null,
            "order_in_category ASC",
            "1"
        )
        if (cursor.moveToFirst()) {
            val belowId = cursor.getLong(cursor.getColumnIndexOrThrow(DbSchema.COL_ID))
            val belowOrder = cursor.getInt(cursor.getColumnIndexOrThrow("order_in_category"))
            updateTaskOrder(todo.id, belowOrder)
            updateTaskOrder(belowId, todo.order)
        }
        cursor.close()
    }

    suspend fun deleteTodo(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.update(DbSchema.TABLE_NAME, ContentValues().apply {
            put(DbSchema.COL_DELETED, 1)
        }, "${DbSchema.COL_ID} = ?", arrayOf(id.toString()))
    }

    suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        val categories = mutableListOf<Category>()
        readableDatabase.query("categories", null, null, null, null, null, "position ASC")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    categories.add(
                        Category(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            position = cursor.getInt(cursor.getColumnIndexOrThrow("position"))
                        )
                    )
                }
            }
        categories
    }

    suspend fun getMaxCategoryPosition(): Int = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT MAX(position) FROM categories", null).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0)
            } else 0
        }
    }

    suspend fun addCategory(name: String) = withContext(Dispatchers.IO) {
        val maxPos = getMaxCategoryPosition()
        writableDatabase.insert("categories", null, ContentValues().apply {
            put("name", name)
            put("position", maxPos + 1)
        })
    }

    suspend fun updateCategory(category: Category) = withContext(Dispatchers.IO) {
        writableDatabase.update("categories", ContentValues().apply {
            put("name", category.name)
            put("position", category.position)
        }, "id = ?", arrayOf(category.id.toString()))
    }

    suspend fun deleteCategory(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete("categories", "id = ?", arrayOf(id.toString()))
    }

    suspend fun saveGoal(goalText: String) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict(
            "user_goal", null,
            ContentValues().apply {
                put("id", 1)
                put("goal_text", goalText)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    suspend fun loadGoal(): String = withContext(Dispatchers.IO) {
        readableDatabase.query(
            "user_goal",
            arrayOf("goal_text"),
            "id = ?",
            arrayOf("1"),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow("goal_text"))
            } else {
                "Start your new habit"
            }
        }
    }

    suspend fun getChainEntries(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, Boolean>()
        readableDatabase.query(
            ChainSchema.TABLE_NAME,
            null, null, null, null, null,
            ChainSchema.COL_DATE + " ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val date = cursor.getString(cursor.getColumnIndexOrThrow(ChainSchema.COL_DATE))
                val isDone = cursor.getInt(cursor.getColumnIndexOrThrow(ChainSchema.COL_DONE)) == 1
                map[date] = isDone
            }
        }
        map
    }

    suspend fun setChainEntry(date: String, isDone: Boolean) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict(
            ChainSchema.TABLE_NAME, null,
            ContentValues().apply {
                put(ChainSchema.COL_DATE, date)
                put(ChainSchema.COL_DONE, if (isDone) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun clearChainEntries() {
        val db = writableDatabase
        db.delete(ChainSchema.TABLE_NAME, null, null)
        db.close()
    }

    suspend fun clearCompletedTodosByCategory(category: Category) = withContext(Dispatchers.IO) {
        writableDatabase.update(
            DbSchema.TABLE_NAME,
            ContentValues().apply { put(DbSchema.COL_DELETED, 1) },
            "${DbSchema.COL_DONE} = 1 AND ${DbSchema.COL_DELETED} = 0 AND ${DbSchema.COL_CATEGORY} = ?",
            arrayOf(category.id.toString())
        )
    }

    suspend fun updateCategoryPosition(categoryId: Long, newPosition: Int) = withContext(Dispatchers.IO) {
        writableDatabase.update(
            "categories",
            ContentValues().apply {
                put("position", newPosition)
            },
            "id = ?",
            arrayOf(categoryId.toString())
        )
    }

    suspend fun normalizeCategoryPositions() = withContext(Dispatchers.IO) {
        val categories = getCategories().sortedBy { it.position }
        categories.forEachIndexed { index, category ->
            updateCategoryPosition(category.id, index)
        }
    }

    suspend fun moveCategoryUp(category: Category) = withContext(Dispatchers.IO) {
        val categories = getCategories().sortedBy { it.position }
        val idx = categories.indexOfFirst { it.id == category.id }
        if (idx > 0) {
            // Swap with the previous
            val prev = categories[idx - 1]
            updateCategoryPosition(category.id, prev.position)
            updateCategoryPosition(prev.id, category.position)
            normalizeCategoryPositions()
        }
    }

    suspend fun moveCategoryDown(category: Category) = withContext(Dispatchers.IO) {
        val categories = getCategories().sortedBy { it.position }
        val idx = categories.indexOfFirst { it.id == category.id }
        if (idx < categories.size - 1) {
            val next = categories[idx + 1]
            updateCategoryPosition(category.id, next.position)
            updateCategoryPosition(next.id, category.position)
            normalizeCategoryPositions()
        }
    }


}
