package com.tanonb.scandoc.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.tanonb.scandoc.ui.Routes

/** Écran principal : onglets Accueil / Fichiers, bouton central de numérisation et son menu. */
@Composable
fun MainShell(nav: NavHostController) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var focusSearch by remember { mutableStateOf(false) }

    BackHandler(menuOpen) { menuOpen = false }
    BackHandler(!menuOpen && tab != 0) { tab = 0 }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (tab) {
            0 -> HomeTab(nav, onSearch = { focusSearch = true; tab = 1 })
            else -> FilesTab(nav, focusSearch = focusSearch, onSearchFocused = { focusSearch = false })
        }

        // Voile + menu de création
        AnimatedVisibility(menuOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { menuOpen = false },
            )
        }
        AnimatedVisibility(
            menuOpen,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(bottom = 104.dp).padding(horizontal = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MenuPill(Icons.AutoMirrored.Outlined.MergeType, "Combiner des fichiers") { menuOpen = false; nav.navigate(Routes.combine()) }
                MenuPill(Icons.Outlined.Collections, "Créer avec photos") { menuOpen = false; nav.navigate(Routes.gallery()) }
                MenuPill(Icons.Outlined.CameraAlt, "Créer une numérisation") { menuOpen = false; nav.navigate(Routes.camera()) }
            }
        }

        BottomBar(
            tab = tab,
            menuOpen = menuOpen,
            onTab = { tab = it; menuOpen = false },
            onFab = { menuOpen = !menuOpen },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MenuPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        Row(Modifier.padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp))
            Spacer(Modifier.width(18.dp))
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun BottomBar(tab: Int, menuOpen: Boolean, onTab: (Int) -> Unit, onFab: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().height(68.dp),
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                TabItem(Icons.Outlined.Home, "Accueil", tab == 0, Modifier.weight(1f)) { onTab(0) }
                Spacer(Modifier.width(84.dp))
                TabItem(Icons.Outlined.Description, "Fichiers", tab == 1, Modifier.weight(1f)) { onTab(1) }
            }
        }
        Surface(
            onClick = onFab,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(76.dp).offset(y = (-10).dp).shadow(10.dp, CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedVisibility(!menuOpen, enter = scaleIn(), exit = scaleOut()) {
                    Icon(Icons.Filled.AddAPhoto, "Nouvelle numérisation", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                AnimatedVisibility(menuOpen, enter = scaleIn(), exit = scaleOut()) {
                    Icon(Icons.Filled.Close, "Fermer", tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
        }
    }
}

@Composable
private fun TabItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier.fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}
