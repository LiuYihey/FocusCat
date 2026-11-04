package com.focusguard.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.data.local.entity.QuoteEntity
import com.focusguard.app.ui.components.GlassDialog
import com.focusguard.app.ui.components.GlassDialogActions
import com.focusguard.app.ui.components.SoftTextField

/**
 * 小猫格言管理界面
 *
 * 需求 1：退出被约束应用时不再发放猫粮，改为随机展示一句小猫格言；
 * 格言内容由用户自己编写，可在这里增删改、启用/停用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesScreen(
    onBack: () -> Unit,
    viewModel: QuotesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 错误提示统一为 Snackbar，与 HomeScreen/AppsScreen 一致
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小猫格言管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.showAddDialog()
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加格言")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(state.quotes, key = { it.id }) { quote ->
                QuoteCard(
                    quote = quote,
                    onToggleActive = { viewModel.toggleActive(quote) },
                    onEdit = { viewModel.showEditDialog(quote) },
                    onDelete = { viewModel.deleteQuote(quote) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // 添加/编辑对话框
    if (state.showDialog) {
        EditQuoteDialog(
            editingQuote = state.editing,
            inputText = state.inputText,
            onInputChange = { viewModel.setInputText(it) },
            onDismiss = { viewModel.dismissDialog() },
            onSave = { viewModel.saveQuote() }
        )
    }
}

/**
 * 单条格言卡片
 */
@Composable
private fun QuoteCard(
    quote: QuoteEntity,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (quote.isActive) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quote.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (quote.isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (quote.isCustom) "自定义" else "内置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = quote.isActive,
                onCheckedChange = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleActive()
                }
            )
            IconButton(onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onEdit()
            }) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDelete()
            }) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}

/**
 * 添加/编辑格言对话框
 */
@Composable
private fun EditQuoteDialog(
    editingQuote: QuoteEntity?,
    inputText: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = if (editingQuote == null) "添加格言" else "编辑格言",
        actions = {
            GlassDialogActions(
                dismissLabel = "取消",
                confirmLabel = "保存",
                onDismiss = onDismiss,
                onConfirm = onSave
            )
        }
    ) {
        Column {
            SoftTextField(
                value = inputText,
                onValueChange = onInputChange,
                label = "格言内容",
                placeholder = "如：从现在开始做到快速放下，改变从一点点开始",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2
            )
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
