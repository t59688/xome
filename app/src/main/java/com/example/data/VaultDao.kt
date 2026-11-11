package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_folders WHERE (:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId ORDER BY folder_name ASC")
    fun observeFolders(parentId: Long?): Flow<List<VaultFolderEntity>>

    @Query("SELECT * FROM vault_folders ORDER BY folder_name ASC")
    fun observeAllFolders(): Flow<List<VaultFolderEntity>>

    @Query("SELECT * FROM vault_folders WHERE id = :folderId LIMIT 1")
    suspend fun getFolderById(folderId: Long): VaultFolderEntity?

    @Query("SELECT * FROM vault_folders WHERE parent_id = :parentId ORDER BY folder_name ASC")
    suspend fun getSubfoldersSync(parentId: Long): List<VaultFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: VaultFolderEntity): Long

    @Query("UPDATE vault_folders SET folder_name = :newName WHERE id = :folderId")
    suspend fun renameFolder(folderId: Long, newName: String)

    @Query("DELETE FROM vault_folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    @Query("SELECT * FROM vault_files WHERE (:folderId IS NULL AND folder_id IS NULL) OR folder_id = :folderId ORDER BY imported_at DESC")
    fun observeFiles(folderId: Long?): Flow<List<VaultFileEntity>>

    @Query("SELECT * FROM vault_files ORDER BY imported_at DESC")
    fun observeAllFiles(): Flow<List<VaultFileEntity>>

    @Query("SELECT * FROM vault_files WHERE folder_id = :folderId")
    suspend fun getFilesInFolderSync(folderId: Long): List<VaultFileEntity>

    @Query("SELECT * FROM vault_files WHERE folder_id IS NULL")
    suspend fun getFilesInRootSync(): List<VaultFileEntity>

    @Query("SELECT * FROM vault_files WHERE id = :fileId LIMIT 1")
    suspend fun getFileById(fileId: Long): VaultFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: VaultFileEntity): Long

    @Query("UPDATE vault_files SET original_name = :newName WHERE id = :fileId")
    suspend fun renameFile(fileId: Long, newName: String)

    @Query("UPDATE vault_files SET folder_id = :targetFolderId WHERE id = :fileId")
    suspend fun moveFileToFolder(fileId: Long, targetFolderId: Long?)

    @Query("UPDATE vault_files SET original_name = :newName, folder_id = :targetFolderId WHERE id = :fileId")
    suspend fun updateFileNameAndFolder(fileId: Long, newName: String, targetFolderId: Long?)

    @Query("DELETE FROM vault_files WHERE id = :fileId")
    suspend fun deleteFileById(fileId: Long)

    @Query("SELECT COUNT(*) FROM vault_files")
    fun observeTotalFileCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(original_size), 0) FROM vault_files")
    fun observeTotalPlainBytes(): Flow<Long>

    @Query("SELECT * FROM vault_space_meta LIMIT 1")
    fun observeSpaceMeta(): Flow<VaultSpaceMetaEntity?>

    @Query("SELECT * FROM vault_space_meta LIMIT 1")
    suspend fun getSpaceMetaSync(): VaultSpaceMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpaceMeta(meta: VaultSpaceMetaEntity)
}
