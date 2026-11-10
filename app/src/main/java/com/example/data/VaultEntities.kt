package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_folders",
    indices = [Index(value = ["parent_id"])]
)
data class VaultFolderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,

    @ColumnInfo(name = "folder_name")
    val folderName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vault_files",
    indices = [Index(value = ["folder_id"]), Index(value = ["blob_filename"], unique = true)]
)
data class VaultFileEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "folder_id")
    val folderId: Long? = null,

    @ColumnInfo(name = "original_name")
    val originalName: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "original_size")
    val originalSize: Long,

    @ColumnInfo(name = "blob_filename")
    val blobFilename: String,

    @ColumnInfo(name = "encrypted_thumbnail", typeAffinity = ColumnInfo.BLOB)
    val encryptedThumbnail: ByteArray? = null,

    @ColumnInfo(name = "imported_at")
    val importedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "original_uri_path")
    val originalUriPath: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultFileEntity) return false
        return id == other.id &&
            folderId == other.folderId &&
            originalName == other.originalName &&
            mimeType == other.mimeType &&
            originalSize == other.originalSize &&
            blobFilename == other.blobFilename &&
            importedAt == other.importedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (folderId?.hashCode() ?: 0)
        result = 31 * result + originalName.hashCode()
        result = 31 * result + blobFilename.hashCode()
        return result
    }
}

@Entity(tableName = "vault_space_meta")
data class VaultSpaceMetaEntity(
    @PrimaryKey
    @ColumnInfo(name = "space_id")
    val spaceId: String,

    @ColumnInfo(name = "space_alias")
    val spaceAlias: String,

    @ColumnInfo(name = "is_decoy")
    val isDecoy: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
