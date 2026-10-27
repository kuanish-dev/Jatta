package com.jatta.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun ChatInput(enabled: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val send = {
        if (enabled && text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Напишите по-английски…", color = Neon.Muted) },
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Neon.Cyan,
                unfocusedBorderColor = Neon.Line,
                cursorColor = Neon.Magenta,
                focusedContainerColor = Neon.Panel,
                unfocusedContainerColor = Neon.Panel,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        Dictation { heard -> text = (text + " " + heard).trim() }
        NeonButton(send, enabled = enabled && text.isNotBlank()) { Text("→") }
    }
}

@Composable
private fun Dictation(onHeard: (String) -> Unit) {
    val input = LocalSpeechInput.current ?: return
    if (!input.available) return
    val scope = rememberCoroutineScope()
    var listening by remember { mutableStateOf(false) }
    val color = if (listening) Neon.Magenta else Neon.Cyan
    val breathing = if (listening) Modifier.pulse(scaleBy = 0.12f, periodMs = 600) else Modifier
    val listen = {
        if (listening) {
            input.stop()
        } else {
            scope.launch {
                listening = true
                val spoken = input.listen(onPartial = {}, onLevel = {})
                listening = false
                if (spoken is Spoken.Words) onHeard(spoken.variants.first())
            }
        }
    }
    Canvas(Modifier.size(44.dp).pressable { listen() }.then(breathing)) {
        val radius = size.minDimension / 2
        drawCircle(color, radius, alpha = if (listening) 0.25f else 0.1f)
        drawCircle(color, radius - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        drawMicrophone(color, size.minDimension * 0.24f)
    }
}
