package com.example.houseelf.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.houseelf.model.CatalogModel
import com.example.houseelf.model.ModelCatalog
import com.example.houseelf.model.ModelInfo
import com.example.houseelf.model.ModelStorage
import com.example.houseelf.model.formatSize
import com.example.houseelf.ui.components.DuoButton
import com.example.houseelf.ui.components.DuoCard
import com.example.houseelf.ui.components.DuoDialog
import com.example.houseelf.ui.components.DuoIconButton
import com.example.houseelf.ui.components.DuoKind
import com.example.houseelf.ui.components.DuoSwitch
import com.example.houseelf.ui.components.IconBadge
import com.example.houseelf.ui.components.ScreenHeader
import com.example.houseelf.ui.components.dashedBorder
import com.example.houseelf.ui.hedgehog.Hedgehog
import com.example.houseelf.ui.icons.DuoIcons
import com.example.houseelf.ui.theme.Duo

@Composable
fun LibraryScreen(
    models: List<ModelInfo>,
    activeModel: String?,
    error: String?,
    freeRamBytes: Long,
    totalRamBytes: Long,
    autoLoad: Boolean,
    confirmActions: Boolean,
    contextTokens: Int,
    loadedContextTokens: Int?,
    loadedContextSetting: Int?,
    chatHasMessages: Boolean,
    onSelect: (String) -> Unit,
    onUnload: () -> Unit,
    onAutoLoadChange: (Boolean) -> Unit,
    onConfirmActionsChange: (Boolean) -> Unit,
    onContextChange: (Int) -> Unit,
    onReloadModel: () -> Unit,
    onDelete: (String) -> Unit,
    onDownload: (CatalogModel) -> Unit,
    onPickFile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<ModelInfo?>(null) }
    var pendingUnload by remember { mutableStateOf(false) }
    val canGoBack = activeModel != null
    // Что ещё можно скачать: модели из каталога, которых на телефоне пока нет
    val downloadable = ModelCatalog.models.filter { m -> models.none { it.fileName.equals(m.fileName, ignoreCase = true) } }

    Column(modifier.fillMaxSize().background(Duo.Bg)) {
        ScreenHeader(
            title = "Мозг домового",
            subtitle = if (totalRamBytes > 0) {
                "Свободно ${formatSize(freeRamBytes)} из ${formatSize(totalRamBytes)} памяти"
            } else null,
            onBack = if (canGoBack) onBack else null,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (error != null) {
                item {
                    DuoCard(Modifier.fillMaxWidth(), borderColor = Duo.Red) {
                        Text(
                            error,
                            Modifier.padding(14.dp),
                            color = Duo.Red,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item {
                DuoCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Hedgehog(64.dp)
                        Column {
                            Text(
                                if (models.isEmpty()) "Нужна модель" else "Думает на телефоне",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                            )
                            Text(
                                if (models.isEmpty()) {
                                    "Выбери модель ниже и скачай её или загрузи свою. Дальше чат работает без интернета."
                                } else {
                                    "Переписки остаются у вас. Интернет нужен только для скачивания модели."
                                },
                                Modifier.padding(top = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Duo.Muted,
                            )
                        }
                    }
                }
            }

            items(models, key = { it.fileName }) { model ->
                ModelCard(
                    model = model,
                    isLoaded = model.fileName == activeModel,
                    onOpenOrLoad = { onSelect(model.fileName) },
                    onUnload = { if (chatHasMessages) pendingUnload = true else onUnload() },
                    onDelete = { pendingDelete = model },
                )
            }

            if (models.isNotEmpty()) {
                item {
                    DuoCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            SettingRow(
                                title = "Загружать при запуске",
                                hint = "Последняя модель сама встанет в память",
                                checked = autoLoad,
                                onChange = onAutoLoadChange,
                                divider = true,
                            )
                            SettingRow(
                                title = "Спрашивать перед действиями",
                                hint = "Карточка с кнопками вместо того, чтобы сразу добавлять, менять и удалять события",
                                checked = confirmActions,
                                onChange = onConfirmActionsChange,
                                divider = false,
                            )
                        }
                    }
                }
            }

            if (models.isNotEmpty()) {
                item {
                    ContextCard(
                        models = models,
                        activeModel = activeModel,
                        freeRamBytes = freeRamBytes,
                        contextTokens = contextTokens,
                        loadedContextTokens = loadedContextTokens,
                        loadedContextSetting = loadedContextSetting,
                        onChange = onContextChange,
                        onReload = onReloadModel,
                    )
                }
            }

            item {
                Column(
                    Modifier.fillMaxWidth().dashedBorder(Duo.Dashed).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        if (models.isEmpty()) "Как получить модель" else "Другая модель",
                        style = MaterialTheme.typography.titleSmall,
                        color = Duo.Muted,
                    )
                    downloadable.forEach { model ->
                        CatalogCard(model, onDownload = { onDownload(model) })
                    }
                    if (downloadable.isNotEmpty()) {
                        Text(
                            "Модели большие — лучше качать по Wi-Fi",
                            style = MaterialTheme.typography.bodySmall,
                            color = Duo.Muted,
                        )
                    }
                    DuoButton(
                        "Из памяти телефона",
                        onPickFile,
                        Modifier.fillMaxWidth(),
                        kind = if (downloadable.isNotEmpty()) DuoKind.Outline else DuoKind.Primary,
                        icon = DuoIcons.Folder,
                    )
                    Text(
                        "Файл модели с расширением ${ModelStorage.MODEL_EXT}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Duo.Muted,
                    )
                }
            }
        }
    }

    if (pendingUnload) {
        DuoDialog(
            title = "Выгрузить модель из памяти?",
            text = "Текущий диалог закроется: вместе с моделью из памяти уйдёт и его контекст. " +
                "Файл модели останется на телефоне.",
            confirmText = "Выгрузить",
            dismissText = "Отмена",
            onConfirm = {
                pendingUnload = false
                onUnload()
            },
            onDismiss = { pendingUnload = false },
        )
    }

    pendingDelete?.let { model ->
        DuoDialog(
            title = "Удалить модель?",
            text = "«${model.displayName}» (${formatSize(model.sizeBytes)}) будет удалена с телефона. " +
                (if (model.fileName == activeModel) "Текущий чат закроется. " else "") +
                "Чтобы пользоваться ей снова, её нужно будет скачать или загрузить заново.",
            confirmText = "Удалить",
            confirmColor = Duo.Red,
            dismissText = "Отмена",
            onConfirm = {
                onDelete(model.fileName)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Модель из каталога, которую можно скачать: название, размер, что умеет и кнопка. */
@Composable
private fun CatalogCard(model: CatalogModel, onDownload: () -> Unit) {
    DuoCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(model.sizeLabel, style = MaterialTheme.typography.labelLarge, color = Duo.Muted)
            }
            Text(model.description, style = MaterialTheme.typography.bodySmall, color = Duo.Muted)
            if (model.ramHint != null) {
                Text(model.ramHint, style = MaterialTheme.typography.bodySmall, color = Duo.Muted)
            }
            Spacer(Modifier.height(4.dp))
            DuoButton("Скачать", onDownload, Modifier.fillMaxWidth(), icon = DuoIcons.Download)
        }
    }
}

@Composable
private fun SettingRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .drawBehind {
                if (divider) {
                    drawLine(
                        color = Duo.Track,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = Duo.Muted)
        }
        Spacer(Modifier.width(12.dp))
        DuoSwitch(checked, onChange)
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    isLoaded: Boolean,
    onOpenOrLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
) {
    DuoCard(Modifier.fillMaxWidth(), borderColor = if (isLoaded) Duo.Teal else Duo.Line) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(
                    DuoIcons.Chip,
                    background = if (isLoaded) Duo.TealSoft else Duo.Track,
                    tint = if (isLoaded) Duo.TealDark else Duo.Muted,
                )
                Column(Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        (if (isLoaded) "● Загружена в память" else "Не в памяти") + " · " + formatSize(model.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLoaded) Duo.TealDark else Duo.Muted,
                    )
                }
                DuoIconButton(DuoIcons.Trash, "Удалить модель", onDelete, tint = Duo.Red)
            }
            Spacer(Modifier.height(14.dp))
            if (isLoaded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DuoButton("Открыть чат", onOpenOrLoad, Modifier.weight(1f))
                    DuoButton("Выгрузить", onUnload, Modifier.weight(1f), kind = DuoKind.Outline)
                }
            } else {
                DuoButton("Загрузить в память", onOpenOrLoad, Modifier.fillMaxWidth())
            }
        }
    }
}
