package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Folder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Int): Folder?

    @Query("SELECT * FROM folders WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Folder?

    @Query("SELECT * FROM folders WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): Folder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: Folder): Long

    @Update
    suspend fun update(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun count(): Int

    @Query("SELECT * FROM folders WHERE parentId IS NULL AND isTrash = 0 ORDER BY sortOrder ASC")
    fun getRootFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentId = :parentId ORDER BY sortOrder ASC")
    fun getChildren(parentId: Int): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE isTrash = 0 ORDER BY sortOrder ASC")
    fun getAllForTree(): Flow<List<Folder>>

    @Query("UPDATE folders SET parentId = :newParent WHERE id = :id")
    suspend fun reparent(id: Int, newParent: Int?)

    @Query("SELECT COUNT(*) FROM meetings WHERE folderId = :id AND isDeleted = 0")
    suspend fun recordingCount(id: Int): Int

    @Query("SELECT COUNT(*) FROM folders WHERE parentId = :id")
    suspend fun childCount(id: Int): Int

    @Query("SELECT SUM(durationSeconds) FROM meetings WHERE folderId = :id AND isDeleted = 0")
    suspend fun totalDuration(id: Int): Long?

    @Query("UPDATE folders SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Int, order: Int)
}
