package com.tanonb.scandoc.export

import com.tanonb.scandoc.ocr.OcrPage
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Document Word (.docx) modifiable, construit à partir du texte reconnu (code pur).
 * Un paragraphe par bloc de texte, un saut de page entre les pages numérisées.
 */
object DocxWriter {

    fun write(out: OutputStream, title: String, pages: List<OcrPage>) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>""")
            put("word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""")
            put("docProps/core.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:title>${esc(title)}</dc:title><dc:creator>ScanDoc</dc:creator>
</cp:coreProperties>""")
            put("word/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:lang w:val="fr-FR"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
</w:styles>""")
            put("word/document.xml", documentXml(pages))
        }
    }

    fun documentXml(pages: List<OcrPage>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
        pages.forEachIndexed { i, page ->
            if (i > 0) append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            if (page.blocks.isEmpty()) append("""<w:p><w:r><w:rPr><w:i/></w:rPr><w:t>(Aucun texte reconnu sur la page ${i + 1})</w:t></w:r></w:p>""")
            page.blocks.forEach { block ->
                append("<w:p>")
                block.forEachIndexed { j, line ->
                    if (j > 0) append("<w:r><w:br/></w:r>")
                    append("""<w:r><w:t xml:space="preserve">${esc(line.text)}</w:t></w:r>""")
                }
                append("</w:p>")
            }
        }
        append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1417" w:right="1417" w:bottom="1417" w:left="1417" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>""")
        append("</w:body></w:document>")
    }

    fun esc(s: String) = buildString {
        s.forEach { c ->
            when {
                c == '&' -> append("&amp;"); c == '<' -> append("&lt;"); c == '>' -> append("&gt;"); c == '"' -> append("&quot;")
                c.code < 0x20 && c != '\t' -> Unit // caractères de contrôle interdits en XML
                else -> append(c)
            }
        }
    }
}
