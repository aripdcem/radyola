package com.aripd.radyola.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aripd.radyola.UiState

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = { Text("İstasyon ara…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Aramayı temizle")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    )
}

/**
 * Ülke ve tür filtreleri — web sürümündeki iki sıra "pill" düğmenin karşılığı.
 * Favoriler seçici ülke sırasının başında durur.
 */
@Composable
fun FilterSection(
    state: UiState,
    onCountrySelected: (String?) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onToggleFavorites: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item {
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = onToggleFavorites,
                    label = { Text("Favoriler") },
                    leadingIcon = {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = chipColors()
                )
            }
            item {
                FilterChip(
                    selected = state.selectedCountry == null,
                    onClick = { onCountrySelected(null) },
                    label = { Text("Tüm ülkeler") },
                    shape = RoundedCornerShape(20.dp),
                    colors = chipColors()
                )
            }
            items(state.countries, key = { it }) { country ->
                FilterChip(
                    selected = state.selectedCountry == country,
                    onClick = {
                        onCountrySelected(if (state.selectedCountry == country) null else country)
                    },
                    label = { Text(country) },
                    shape = RoundedCornerShape(20.dp),
                    colors = chipColors()
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item {
                FilterChip(
                    selected = state.selectedGenre == null,
                    onClick = { onGenreSelected(null) },
                    label = { Text("Tüm türler") },
                    shape = RoundedCornerShape(20.dp),
                    colors = chipColors()
                )
            }
            items(state.genres, key = { it }) { genre ->
                FilterChip(
                    selected = state.selectedGenre == genre,
                    onClick = { onGenreSelected(if (state.selectedGenre == genre) null else genre) },
                    label = { Text(genre) },
                    shape = RoundedCornerShape(20.dp),
                    colors = chipColors()
                )
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)
