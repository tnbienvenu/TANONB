package com.tanonb.scandoc.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.tanonb.scandoc.app
import com.tanonb.scandoc.data.PageSource
import com.tanonb.scandoc.imaging.DocumentDetector
import com.tanonb.scandoc.imaging.NPoint
import com.tanonb.scandoc.imaging.Quad
import com.tanonb.scandoc.ui.components.BusyOverlay
import com.tanonb.scandoc.ui.components.ConfirmDialog
import com.tanonb.scandoc.ui.components.FileThumbnail
import com.tanonb.scandoc.ui.components.toast
import com.tanonb.scandoc.ui.openNewDocument
import com.tanonb.scandoc.ui.theme.Accent
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/**
 * « Créer une numérisation » : appareil photo intégré. Le contour du document est détecté en direct,
 * on enchaîne les pages, puis chaque photo est recadrée et redressée automatiquement.
 */
@Composable
fun CameraScreen(nav: NavHostController, docId: Long) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    val shots = remember { mutableStateListOf<File>() }
    var flash by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var liveQuad by remember { mutableStateOf<Quad?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirmQuit by remember { mutableStateOf(false) }

    val ratio = remember { ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build() }
    val capture = remember {
        ImageCapture.Builder().setResolutionSelector(ratio).setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysis = remember {
        ImageAnalysis.Builder().setResolutionSelector(ratio).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
            var last = 0L
            it.setAnalyzer(analysisExecutor) { image ->
                val now = System.currentTimeMillis()
                if (now - last > 250) {
                    last = now
                    liveQuad = runCatching { detectFrame(image) }.getOrNull()?.takeUnless { q -> q.isFull }
                }
                image.close()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown(); runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }
    LaunchedEffect(flash) { capture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }

    fun finish() {
        if (shots.isEmpty()) { nav.popBackStack(); return }
        val files = shots.toList()
        scope.launch {
            progress = 0 to files.size
            val onProgress: (Int, Int) -> Unit = { i, n -> progress = i to n }
            val sources = files.map { PageSource.FromFile(it) }
            val id = runCatching {
                if (docId < 0) context.app.background { createWithPages(sources, autoCrop = true, onProgress = onProgress) }
                else context.app.background { addPages(docId, sources, autoCrop = true, onProgress = onProgress); docId }
            }.getOrNull()
            files.forEach { it.delete() }
            progress = null
            when {
                id == null -> context.toast("Échec de l'enregistrement des photos.")
                docId < 0 -> nav.openNewDocument(id)
                else -> nav.popBackStack()
            }
        }
    }

    fun takePicture() {
        if (!granted || capturing) return
        capturing = true
        val file = context.app.repository.newCaptureFile()
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { capturing = false; shots += file }
                override fun onError(exception: ImageCaptureException) { capturing = false; context.toast("Échec de la prise de vue : ${exception.message}") }
            },
        )
    }

    BackHandler { if (shots.isNotEmpty()) confirmQuit = true else nav.popBackStack() }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (shots.isNotEmpty()) confirmQuit = true else nav.popBackStack() }) { Icon(Icons.Filled.Close, "Fermer", tint = Color.White) }
            Text("Document", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { flash = !flash }) { Icon(if (flash) Icons.Filled.FlashOn else Icons.Filled.FlashOff, "Flash", tint = Color.White) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (granted) {
                Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }.also { view ->
                                val future = ProcessCameraProvider.getInstance(ctx)
                                future.addListener({
                                    runCatching {
                                        val provider = future.get()
                                        val preview = Preview.Builder().setResolutionSelector(ratio).build().also { it.surfaceProvider = view.surfaceProvider }
                                        provider.unbindAll()
                                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                                    }.onFailure { context.toast("Caméra indisponible : ${it.message}") }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    liveQuad?.let { q ->
                        Canvas(Modifier.fillMaxSize()) {
                            val pts = q.points.map { Offset(it.x * size.width, it.y * size.height) }
                            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                            drawPath(path, Accent.copy(alpha = 0.2f))
                            drawPath(path, Accent, style = Stroke(width = 3.dp.toPx()))
                        }
                    }
                    if (capturing) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.3f)))
                }
                Text(
                    if (liveQuad != null) "Document détecté" else "Cadrez le document",
                    color = Color.White, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Autorisez l'appareil photo pour numériser.", color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Autoriser") }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.size(64.dp)) {
                shots.lastOrNull()?.let {
                    FileThumbnail(it.absolutePath, maxDim = 200, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).border(2.dp, Color.White, RoundedCornerShape(6.dp)))
                    Surface(shape = CircleShape, color = Accent, modifier = Modifier.align(Alignment.TopEnd).size(24.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("${shots.size}", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            Surface(
                onClick = ::takePicture,
                shape = CircleShape, color = Color.White,
                modifier = Modifier.size(78.dp).border(4.dp, Accent, CircleShape),
            ) {}
            Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
                if (shots.isNotEmpty()) Button(onClick = ::finish) { Text("Terminer") }
            }
        }
    }
    if (confirmQuit) ConfirmDialog(
        "Abandonner ${shots.size} photo(s) ?", "Les pages prises ne seront pas enregistrées.", "Abandonner",
        onDismiss = { confirmQuit = false },
    ) { confirmQuit = false; shots.forEach { it.delete() }; nav.popBackStack() }
    progress?.let { (i, n) -> BusyOverlay("Recadrage de la page ${minOf(i + 1, n)} sur $n…", if (n > 0) i.toFloat() / n else null) }
}

/** Détection sur le plan de luminance (Y) d'une image d'analyse, ramenée dans le sens d'affichage. */
private fun detectFrame(image: ImageProxy): Quad? {
    val plane = image.planes[0]
    val buf = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val step = maxOf(1, image.width / 200)
    val w = image.width / step
    val h = image.height / step
    val lum = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        lum[y * w + x] = buf.get(y * step * rowStride + x * step * pixelStride).toInt() and 0xff
    }
    val q = DocumentDetector.detect(lum, w, h) ?: return null
    val rot = image.imageInfo.rotationDegrees
    fun r(p: NPoint) = when (rot) {
        90 -> NPoint(1 - p.y, p.x)
        180 -> NPoint(1 - p.x, 1 - p.y)
        270 -> NPoint(p.y, 1 - p.x)
        else -> p
    }
    return Quad.ordered(q.points.map(::r))
}
