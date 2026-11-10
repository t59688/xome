package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.crypto.VaultCryptoEngine
import java.io.File
import java.util.Arrays
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        VaultFolderEntity::class,
        VaultFileEntity::class,
        VaultSpaceMetaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var sqlCipherLoaded = false

        private fun ensureSqlCipherLoaded() {
            if (sqlCipherLoaded) return
            try {
                System.loadLibrary("sqlcipher")
                sqlCipherLoaded = true
            } catch (e: LinkageError) {
                throw IllegalStateException("Encrypted database unavailable: SQLCipher could not be loaded", e)
            }
        }

        /**
         * Opens the space-isolated SQLCipher encrypted database at `db/<spaceId>.db`
         * using a 256-bit key derived from the space's MasterKey.
         */
        fun openSpaceDatabase(
            context: Context,
            dbFile: File,
            masterKey: ByteArray
        ): VaultDatabase {
            ensureSqlCipherLoaded()
            dbFile.parentFile?.mkdirs()
            val dbKeyBytes = VaultCryptoEngine.deriveDatabaseKeyFromMasterKey(masterKey)
            return try {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    dbFile.absolutePath
                )
                val factory = SupportOpenHelperFactory(dbKeyBytes.copyOf())
                builder.openHelperFactory(factory)
                builder.build()
            } finally {
                Arrays.fill(dbKeyBytes, 0.toByte())
            }
        }
    }
}
