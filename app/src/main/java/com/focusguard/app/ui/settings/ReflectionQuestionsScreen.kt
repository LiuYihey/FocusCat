package com.focusguard.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.data.local.entity.ReflectionQuestionEntity
import com.focusguard.app.ui.components.CollapsibleSection
import com.focusguard.app.ui.components.GlassDialog
import com.focusguard.app.ui.components.GlassDialogActions
import com.focusguard.app.ui.components.SoftTextField

/**
 * 反思问题管理界面
 * 用户可查看、添加、编辑、启用/禁用、删除反思问题
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflectionQuestionsScreen(
    onBack: () -> Unit,
    viewModel: ReflectionQuestionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 错误提示统一为 Snackbar，与 HomeScreen/AppsScreen 一致
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("反思问题管理") },
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
                Icon(Icons.Filled.Add, contentDescription = "添加问题")
            }
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
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

                items(state.questions, key = { it.id }) { question ->
                    QuestionCard(
                        question = question,
                        onToggleActive = { viewModel.toggleActive(question) },
                        onEdit = { viewModel.showEditDialog(question) },
                        onDelete = { viewModel.deleteQuestion(question) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // 添加/编辑对话框
    if (state.showEditDialog) {
        EditQuestionDialog(
            editingQuestion = state.editingQuestion,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { text, placeholder, type, options ->
                viewModel.saveQuestion(text, placeholder, type, options)
            }
        )
    }
}

/**
 * 单个问题卡片
 */
@Composable
private fun QuestionCard(
    question: ReflectionQuestionEntity,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (question.isActive) {
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
                    text = question.questionText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (question.isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                // 题型标签 + 选项预览（需求 3）
                val typeLabel = when (question.questionType) {
                    ReflectionQuestionEntity.TYPE_CHOICE -> "选择题"
                    ReflectionQuestionEntity.TYPE_DURATION -> "使用时长"
                    else -> "开放问答"
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$typeLabel · ${question.options.size} 个选项",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (question.options.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = question.options.joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (question.isCustom) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "自定义",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = question.isActive,
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
 * 添加/编辑问题对话框
 *
 * 需求 3：支持自定义题目类型与选项列表
 * - 开放问答：保留输入框提示
 * - 选择题：至少 2 个选项，点击即答
 * - 使用时长：选项中的数字代表分钟数
 */
@Composable
private fun EditQuestionDialog(
    editingQuestion: ReflectionQuestionEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, List<String>) -> Unit
) {
    var questionText by remember(editingQuestion) {
        mutableStateOf(editingQuestion?.questionText ?: "")
    }
    var placeholder by remember(editingQuestion) {
        mutableStateOf(editingQuestion?.placeholder ?: "")
    }
    var questionType by remember(editingQuestion) {
        mutableStateOf(editingQuestion?.questionType ?: ReflectionQuestionEntity.TYPE_CHOICE)
    }
    var options by remember(editingQuestion) {
        mutableStateOf<List<String>>(
            editingQuestion?.options?.toMutableList()
                ?: mutableListOf("消遣一下", "完成具体任务", "只是想看看", "其他")
        )
    }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = if (editingQuestion == null) "添加问题" else "编辑问题",
        actions = {
            GlassDialogActions(
                dismissLabel = "取消",
                confirmLabel = "保存",
                onDismiss = onDismiss,
                onConfirm = { onSave(questionText, placeholder, questionType, options) }
            )
        }
    ) {
        Column {
            // 问题内容
            SoftTextField(
                value = questionText,
                onValueChange = { questionText = it },
                label = "问题内容",
                placeholder = "你进入这个 app 是来做什么的？",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2
            )
            Spacer(modifier = Modifier.height(18.dp))

            // 题型选择：自定义胶囊按钮组，替代原生 FilterChip 的方正描边
            Text(
                text = "题型",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ReflectionQuestionEntity.TYPE_CHOICE to "选择题",
                    ReflectionQuestionEntity.TYPE_DURATION to "使用时长",
                    ReflectionQuestionEntity.TYPE_OPEN to "开放问答"
                ).forEach { (type, label) ->
                    TypeSegmentChip(
                        label = label,
                        selected = questionType == type,
                        onClick = { questionType = type }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Grid 0fr→1fr 折叠：切换题型时，内容区高度平滑过渡
            CollapsibleSection(expanded = questionType == ReflectionQuestionEntity.TYPE_OPEN) {
                SoftTextField(
                    value = placeholder,
                    onValueChange = { placeholder = it },
                    label = "输入框提示（可选）",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            CollapsibleSection(expanded = questionType != ReflectionQuestionEntity.TYPE_OPEN) {
                Column {
                    Text(
                        text = if (questionType == ReflectionQuestionEntity.TYPE_DURATION) {
                            "时长选项（数字为分钟数，如 15 或 1 小时）"
                        } else {
                            "选项（至少 2 个）"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    options.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SoftTextField(
                                value = option,
                                onValueChange = { newValue ->
                                    options = options.toMutableList().also {
                                        it[index] = newValue
                                    }
                                },
                                label = "选项 ${index + 1}",
                                placeholder = if (questionType == ReflectionQuestionEntity.TYPE_DURATION) {
                                    "如 15 分钟"
                                } else {
                                    "选项文案"
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    options = options.toMutableList().also {
                                        it.removeAt(index)
                                    }
                                },
                                enabled = options.size > 2,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除选项",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "+ 添加选项",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { options = options + "新选项" }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

    }
}

/**
 * 题型分段选择器（自定义胶囊按钮）
 *
 * 替代原生 FilterChip：原生版本有方正描边 + 硬边，与整体圆润调性不符。
 * 这里用胶囊形状 + 主色填充表达选中态，视觉更轻。
 */
@Composable
private fun TypeSegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.surfaceVariant,
        animationSpec = tween(200),
        label = "chip_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "chip_fg"
    )

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}
