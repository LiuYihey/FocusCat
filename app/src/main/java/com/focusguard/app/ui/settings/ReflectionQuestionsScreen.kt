package com.focusguard.app.ui.settings

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.data.local.entity.ReflectionQuestionEntity

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
                    Text(
                        text = "这些问题会在你打开约束应用时弹出",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (editingQuestion == null) "添加问题" else "编辑问题")
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("问题内容") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 题型选择
                Text(
                    text = "题型",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReflectionQuestionEntity.TYPE_CHOICE to "选择题",
                        ReflectionQuestionEntity.TYPE_DURATION to "使用时长",
                        ReflectionQuestionEntity.TYPE_OPEN to "开放问答"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = questionType == type,
                            onClick = { questionType = type },
                            label = { Text(label, fontSize = 13.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (questionType == ReflectionQuestionEntity.TYPE_OPEN) {
                    OutlinedTextField(
                        value = placeholder,
                        onValueChange = { placeholder = it },
                        label = { Text("输入框提示（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    // 选项编辑：每行一个输入框 + 删除按钮
                    Text(
                        text = if (questionType == ReflectionQuestionEntity.TYPE_DURATION) {
                            "时长选项（数字为分钟数，如 15 或 1 小时）"
                        } else {
                            "选项（至少 2 个）"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    options.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = option,
                                onValueChange = { newValue ->
                                    options = options.toMutableList().also {
                                        it[index] = newValue
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        text = if (questionType == ReflectionQuestionEntity.TYPE_DURATION) {
                                            "如 15 分钟"
                                        } else {
                                            "选项文案"
                                        },
                                        fontSize = 13.sp
                                    )
                                }
                            )
                            IconButton(
                                onClick = {
                                    options = options.toMutableList().also {
                                        it.removeAt(index)
                                    }
                                },
                                enabled = options.size > 2
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除选项",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = {
                        options = options + "新选项"
                    }) {
                        Text("+ 添加选项", fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(questionText, placeholder, questionType, options) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
