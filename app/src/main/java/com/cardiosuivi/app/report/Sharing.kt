package com.cardiosuivi.app.report

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.cardiosuivi.domain.export.CsvExporter
import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.MeasurementSession
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

object Sharing {

    private fun uri(context: Context, file: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Partage via le système Android (WhatsApp, e-mail, Drive… selon les applications installées). */
    fun share(context: Context, file: File, mime: String, subject: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri(context, file))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Partager le fichier").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(context: Context, file: File, mime: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri(context, file), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    fun writeCsv(context: Context, sessions: List<MeasurementSession>, method: AggregationMethod, from: LocalDate, to: LocalDate, zone: ZoneId): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "CardioSuivi_mesures_${from}_$to.csv")
        val inRange = sessions.filter { !it.localDate.isBefore(from) && !it.localDate.isAfter(to) }
        // BOM UTF-8 pour une ouverture correcte des accents dans les tableurs.
        f.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + CsvExporter.export(inRange, method, zone).toByteArray(Charsets.UTF_8))
        return f
    }

    fun dial(context: Context, number: String) {
        val i = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(i) } catch (_: ActivityNotFoundException) { }
    }
}
