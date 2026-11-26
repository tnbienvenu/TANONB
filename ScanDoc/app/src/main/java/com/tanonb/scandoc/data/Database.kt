package com.tanonb.scandoc.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Taille de page du PDF (voir PageSize). */
    val pageSize: String = "AUTO",
)

/**
 * Une page : photo d'origine conservée (pour pouvoir recadrer à nouveau), paramètres de traitement
 * (recadrage, rotation, filtre, signatures) et image traitée prête à l'export.
 */
@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentId")],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val position: Int,
    val originalPath: String,
    val processedPath: String,
    /** 8 valeurs normalisées : tl.x,tl.y,tr.x,tr.y,br.x,br.y,bl.x,bl.y. */
    val quad: String,
    val rotation: Int,
    val filter: String,
    /** Signatures/tampons posés : JSON (voir [Stamp]). */
    val stamps: String = "[]",
    /** Retouches (nettoyage, stylo, surligneur) : JSON (voir [Mark]). */
    val marks: String = "[]",
    /** Texte reconnu (OCR) : JSON (voir [OcrPage]), null si pas encore reconnu. */
    val ocr: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

data class DocumentSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val coverPath: String?,
)

@Dao
interface ScanDao {
    @Query(
        """SELECT d.id, d.name, d.createdAt, d.updatedAt,
           (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
           (SELECT p.processedPath FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverPath
           FROM documents d ORDER BY d.updatedAt DESC"""
    )
    fun observeDocuments(): Flow<List<DocumentSummary>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDocument(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun document(id: Long): DocumentEntity?

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY position")
    fun observePages(docId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY position")
    suspend fun pages(docId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun page(id: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE ocr IS NULL")
    suspend fun pagesWithoutOcr(): List<PageEntity>

    @Insert suspend fun insert(doc: DocumentEntity): Long
    @Insert suspend fun insert(page: PageEntity): Long
    @Update suspend fun update(doc: DocumentEntity)
    @Update suspend fun update(page: PageEntity)
    @Update suspend fun updatePages(pages: List<PageEntity>)

    @Query("DELETE FROM documents WHERE id = :id") suspend fun deleteDocument(id: Long)
    @Query("DELETE FROM pages WHERE id = :id") suspend fun deletePage(id: Long)
    @Query("UPDATE documents SET updatedAt = :t WHERE id = :id") suspend fun touch(id: Long, t: Long = System.currentTimeMillis())
    @Query("UPDATE documents SET name = :name, updatedAt = :t WHERE id = :id") suspend fun rename(id: Long, name: String, t: Long = System.currentTimeMillis())
    @Query("UPDATE documents SET pageSize = :size, updatedAt = :t WHERE id = :id") suspend fun setPageSize(id: Long, size: String, t: Long = System.currentTimeMillis())
    @Query("UPDATE pages SET ocr = :ocr WHERE id = :id") suspend fun setOcr(id: Long, ocr: String)

    @Transaction
    suspend fun reorder(pages: List<PageEntity>) = updatePages(pages.mapIndexed { i, p -> p.copy(position = i) })
}

@Database(entities = [DocumentEntity::class, PageEntity::class], version = 1, exportSchema = false)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun dao(): ScanDao

    companion object {
        fun create(context: Context): ScanDatabase =
            Room.databaseBuilder(context, ScanDatabase::class.java, "scandoc.db").build()
    }
}
