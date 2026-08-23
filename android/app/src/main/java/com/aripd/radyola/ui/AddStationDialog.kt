package com.aripd.radyola.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import com.aripd.radyola.data.Countries
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Keşfet dizininde olmayan bir kanalı elle eklemek için.
 *
 * Yerel ve niş yayınlar (üniversite radyoları, belediye yayınları)
 * radio-browser'da bulunmuyor; başka türlü uygulamaya giremiyorlar.
 */
@Composable
fun AddStationDialog(
    verifying: Boolean,
    errorMessage: String?,
    knownGenres: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (name: String, url: String, city: String, countryCode: String, genre: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(Countries.names.first()) }
    var genre by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!verifying) onDismiss() },
        title = { Text("Kanal ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("İstasyon adı") },
                    singleLine = true,
                    enabled = !verifying,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Yayın adresi") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    enabled = !verifying,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Şehir (isteğe bağlı)") },
                    placeholder = { Text("İstanbul") },
                    singleLine = true,
                    enabled = !verifying,
                    modifier = Modifier.fillMaxWidth()
                )
                PickerField(
                    value = country,
                    label = "Ülke",
                    options = Countries.names,
                    enabled = !verifying,
                    onValueChange = { country = it }
                )
                PickerField(
                    value = genre,
                    label = "Tür (isteğe bağlı)",
                    options = knownGenres,
                    enabled = !verifying,
                    editable = true,
                    onValueChange = { genre = it }
                )
                if (errorMessage != null) {
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Adres kaydedilmeden önce denenir; çalmayan bir kanal eklenmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name, url, city, Countries.codeOf(country), genre) },
                enabled = !verifying && name.isNotBlank() && url.isNotBlank()
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (verifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (verifying) "Deneniyor…" else "Ekle")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !verifying) { Text("Vazgeç") }
        }
    )
}

/**
 * Açılır seçim alanı.
 *
 * [editable] açıkken listede olmayan bir değer de yazılabilir — tür için
 * gerekli, çünkü kullanıcının aklındaki tür listemizde olmayabilir. Ülke için
 * kapalı: bayrak ISO koduna dayandığı için serbest yazıma yer yok.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerField(
    value: String,
    label: String,
    options: List<String>,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    editable: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (editable) onValueChange(it) },
            readOnly = !editable,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        if (options.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
