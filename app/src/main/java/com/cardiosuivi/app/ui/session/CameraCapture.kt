package com.cardiosuivi.app.ui.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.time.Instant

/**
 * Capture photographique de l'écran du tensiomètre (CameraX), avec import d'image et saisie manuelle.
 * Les photos sont enregistrées temporairement dans le cache privé de l'application et supprimées après la séance.
 */
@Composable
fun CameraCapture(
    index: Int,
    onCaptured: (File, Instant) -> Unit,
    onImported: (android.net.Uri) -> Unit,
    onManual: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onImported) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setFlashMode(ImageCapture.FLASH_MODE_OFF).build() }

    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }

    com.cardiosuivi.app.ui.components.HealthCard {
        com.cardiosuivi.app.ui.components.CardHeader("Photographier le tensiomètre", Icons.Filled.CameraAlt, MaterialTheme.colorScheme.primary)
        Text("Mesure $index : photographiez l'écran du tensiomètre", style = MaterialTheme.typography.titleMedium)
        Text("Cadrez l'écran entier, de face, sans reflet. Les chiffres doivent être nets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (granted) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Color.Black, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }.also { view ->
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                runCatching {
                                    val provider = future.get()
                                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                                }.onFailure { error = "Caméra indisponible : ${it.message}" }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // Guide de cadrage
                Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f).border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp)))
                if (busy) CircularProgressIndicator()
            }
            Button(
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    busy = true
                    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                    val file = File(dir, "mesure_${System.currentTimeMillis()}.jpg")
                    val takenAt = Instant.now()
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) { busy = false; onCaptured(file, takenAt) }
                            override fun onError(exception: ImageCaptureException) { busy = false; error = "Échec de la prise de vue : ${exception.message}" }
                        },
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.CameraAlt, null)
                Text("  Prendre la photo")
            }
        } else {
            Text("L'autorisation d'utiliser l'appareil photo n'est pas accordée. Vous pouvez importer une photo ou saisir les valeurs manuellement.")
            OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Autoriser l'appareil photo") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Image, null); Text(" Importer")
            }
            OutlinedButton(onClick = onManual, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Edit, null); Text(" Saisie manuelle")
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}
