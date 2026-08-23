package com.aripd.radyola.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aripd.radyola.data.RadioStation
import com.aripd.radyola.ui.theme.BrandGradient

@Composable
fun StationRow(
    station: RadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCurrent) accent.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                width = 1.dp,
                color = if (isCurrent) accent.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StationIcon(isPlaying = isPlaying, isCurrent = isCurrent)

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = "${station.flag}  ${station.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            // Keşfet dizininde bir istasyon üç tür birden taşıyabiliyor; çipi
            // sınırlamazsak konumu "…" olacak kadar eziyor. İkisi de yer paylaşır.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (station.location.isNotEmpty()) {
                    Text(
                        text = station.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                val genres = station.genre.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                if (genres.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = genres.take(2).joinToString(" / "),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
                tint = if (isFavorite) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/** Çalarken ekolayzır çubukları, aksi halde oynat üçgeni gösterir. */
@Composable
private fun StationIcon(isPlaying: Boolean, isCurrent: Boolean) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrent) BrandGradient
                else androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.surfaceVariant
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            EqualizerBars()
        } else {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EqualizerBars() {
    val transition = rememberInfiniteTransition(label = "eq")
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(320, 470, 260, 390).forEachIndexed { index, duration ->
            val scale by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(16.dp * scale)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}
