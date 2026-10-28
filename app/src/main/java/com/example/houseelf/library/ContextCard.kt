package com.example.houseelf.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.houseelf.model.ModelCatalog
import com.example.houseelf.model.ModelInfo
import com.example.houseelf.model.formatSize
import com.example.houseelf.ui.components.DuoButton
import com.example.houseelf.ui.components.DuoCard
import com.example.houseelf.ui.components.DuoKind
import com.example.houseelf.ui.components.DuoSlider
import com.example.houseelf.ui.theme.Duo

/**
 * Ползунок «Окно контекста»: сколько токенов модель держит в голове. Чем больше — тем длиннее память
 * разговора, но тем больше оперативной памяти нужно. Подсказка под ползунком и «Авто» считаются по свободной памяти.
 */
@Composable
fun ContextCard(
    models: List<ModelInfo>,
    activeModel: String?,
    freeRamBytes: Long,
    contextTokens: Int,
    loadedContextTokens: Int?,
    loadedContextSetting: Int?,
    onChange: (Int) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Размер контекста можно менять только у моделей, где он не зашит в файл (Gemma)
    val adjustable = models.filter { ModelCatalog.profileFor(it.fileName).customContext }
    val reference = adjustable.firstOrNull { it.fileName == activeModel } ?: adjustable.firstOrNull() ?: return
    val loaded = reference.fileName == activeModel && loadedContextTokens != null

    var position by remember(contextTokens) { mutableIntStateOf(ContextSteps.indexOf(contextTokens)) }
    val tokens = ContextSteps.values[position]
    val hint = ContextAdvisor.advise(
        tokens, reference.sizeBytes, freeRamBytes, if (loaded) loadedContextTokens else null,
    )
    val color = when (hint.verdict) {
        MemoryVerdict.Ok -> Duo.TealDark
        MemoryVerdict.Tight -> Duo.AmberText
        MemoryVerdict.Bad -> Duo.Red
    }
    val needsReload = reference.fileName == activeModel && loadedContextSetting != null && loadedContextSetting != contextTokens

    DuoCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Окно контекста", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (tokens == 0) "Авто · ${hint.tokens}" else "$tokens",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                )
            }
            Text(
                "Сколько текста модель помнит в разговоре. Больше — лучше память, но нужно больше оперативной памяти.",
                style = MaterialTheme.typography.bodySmall,
                color = Duo.Muted,
            )
            DuoSlider(
                value = position,
                steps = ContextSteps.values.size,
                onValueChange = { position = it },
                onValueChangeFinished = { onChange(ContextSteps.values[position]) },
                color = color,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Авто", style = MaterialTheme.typography.bodySmall, color = Duo.Muted)
                Text("2K", style = MaterialTheme.typography.bodySmall, color = Duo.Muted)
                Text("32K", style = MaterialTheme.typography.bodySmall, color = Duo.Muted)
            }
            Text(hint.usage, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Под контекст понадобится примерно ${formatSize(hint.extraBytes)}. ${hint.memory}",
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
            if (adjustable.size > 1 || reference.fileName != activeModel) {
                Text(
                    "Подсказка посчитана для «${reference.displayName}».",
                    style = MaterialTheme.typography.bodySmall,
                    color = Duo.Muted,
                )
            }
            if (needsReload) {
                Text(
                    "Новый размер вступит в силу после перезагрузки модели. Переписка сохранится.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Duo.Muted,
                )
                DuoButton("Применить и перезагрузить", onReload, Modifier.fillMaxWidth(), kind = DuoKind.Outline)
            } else {
                Text(
                    "Применяется при загрузке модели. «Авто» берёт самое большое окно, которое влезает в свободную память на тот момент. Если памяти не хватит, приложение само уменьшит окно.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Duo.Muted,
                )
            }
        }
    }
}
