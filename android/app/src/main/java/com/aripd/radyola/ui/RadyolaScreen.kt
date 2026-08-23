package com.aripd.radyola.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aripd.radyola.MainViewModel
import com.aripd.radyola.UiState
import com.aripd.radyola.ui.theme.RadyolaCyan
import com.aripd.radyola.ui.theme.RadyolaIndigo
import com.aripd.radyola.ui.theme.RadyolaPink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadyolaScreen(state: UiState, viewModel: MainViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var showAddStation by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var curatedResult by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientBackground()

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                AnimatedVisibility(
                    visible = state.current != null,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    PlayerBar(
                        station = state.current,
                        isPlaying = state.isPlaying,
                        isBuffering = state.isBuffering,
                        nowPlayingTrack = state.nowPlayingTrack,
                        sleepTimerRemainingSec = state.sleepTimerRemainingSec,
                        onPlayPause = viewModel::togglePlayPause,
                        onPrevious = viewModel::skipToPrevious,
                        onNext = viewModel::skipToNext,
                        onStop = viewModel::stop
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Header(
                        onAddStation = {
                            addError = null
                            showAddStation = true
                        },
                        onOpenSettings = { showSettings = true },
                        onRefresh = viewModel::loadStations
                    )
                }

                item {
                    SourceToggle(
                        mode = state.mode,
                        directoryLoading = state.directoryLoading,
                        onSelect = viewModel::setMode
                    )
                }

                item {
                    SearchField(
                        query = state.query,
                        placeholder = if (state.isDiscovering) {
                            "${state.stations.size} istasyon içinde ara…"
                        } else {
                            "İstasyon ara…"
                        },
                        onQueryChange = viewModel::setQuery
                    )
                }

                item {
                    FilterSection(
                        state = state,
                        onCountrySelected = viewModel::selectCountry,
                        onGenreSelected = viewModel::selectGenre
                    )
                }

                if (state.isLoading && state.visible.isEmpty()) {
                    item { LoadingRow() }
                } else if (state.visible.isEmpty()) {
                    item {
                        EmptyState(
                            hasFilters = state.hasFilters,
                            isDiscovering = state.isDiscovering,
                            onClear = viewModel::clearFilters
                        )
                    }
                } else {
                    items(state.visible, key = { it.id }) { station ->
                        StationRow(
                            station = station,
                            isCurrent = station.id == state.current?.id,
                            isPlaying = state.isPlaying && station.id == state.current?.id,
                            inMyList = station.id in state.myListIds,
                            isDiscovering = state.isDiscovering,
                            onClick = { viewModel.play(station) },
                            onToggleInMyList = { viewModel.toggleInMyList(station) }
                        )
                    }
                }
            }
        }

        StatusBarScrim(Modifier.align(Alignment.TopCenter))
    }

    if (showAddStation) {
        AddStationDialog(
            verifying = state.verifyingStation,
            errorMessage = addError,
            knownGenres = state.genres,
            onDismiss = { showAddStation = false },
            onSubmit = { name, url, city, countryCode, genre ->
                addError = null
                viewModel.addManualStation(name, url, city, countryCode, genre) { result ->
                    result
                        .onSuccess { showAddStation = false }
                        .onFailure { addError = it.message ?: "Kanal eklenemedi" }
                }
            }
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false; curatedResult = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SettingsSheet(
                state = state,
                onRememberChanged = viewModel::setRememberStation,
                onAutoplayChanged = viewModel::setAutoplayOnStart,
                onSleepTimerSelected = viewModel::setSleepTimer,
                onFetchCurated = {
                    viewModel.addNewCuratedStations { added ->
                        curatedResult = if (added > 0) "$added kanal eklendi" else "Yeni kanal yok"
                    }
                },
                curatedResult = curatedResult
            )
        }
    }
}

/**
 * Durum çubuğunun altından kayan liste satırlarının saatle çakışmaması için
 * ekranın tepesine konan yumuşak geçişli perde.
 */
@Composable
private fun StatusBarScrim(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Brush.verticalGradient(listOf(background, background.copy(alpha = 0f))))
    )
}

/** Web sürümündeki bulanık ışık kürelerinin sakin bir karşılığı. */
@Composable
private fun AmbientBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(RadyolaIndigo.copy(alpha = 0.16f), Color.Transparent),
                    radius = 900f
                )
            )
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        RadyolaPink.copy(alpha = 0.05f),
                        RadyolaCyan.copy(alpha = 0.07f)
                    )
                )
            )
    )
}

@Composable
private fun Header(
    onAddStation: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandTitle(Modifier.weight(1f))
        IconButton(onClick = onAddStation) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Kanal ekle",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "İstasyonları yenile",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "Ayarlar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingRow() {
    Box(
        Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyState(hasFilters: Boolean, isDiscovering: Boolean, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                hasFilters -> "Bu filtrelerle istasyon bulunamadı"
                isDiscovering -> "Keşfet dizini yüklenemedi"
                else -> "Listeniz boş — Keşfet'ten istasyon ekleyin"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (hasFilters) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClear) {
                Text("Filtreleri temizle", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun BrandTitle(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(com.aripd.radyola.ui.theme.BrandGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(com.aripd.radyola.R.drawable.ic_radio),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Radyola",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Dünyadan seçme internet radyoları",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Başlıkta üç ikon var; dar ekranda alt satıra taşmasın.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
