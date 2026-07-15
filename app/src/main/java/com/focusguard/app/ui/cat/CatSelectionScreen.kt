package com.focusguard.app.ui.cat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.ui.components.CatCanvas

/**
 * 猫咪选择界面
 *
 * 简化设计（仅 2 个品种：布偶 + 橘猫）：
 * - 首次启动（非切换模式）：显示品种网格 + 命名输入框，创建第一只猫
 * - 切换模式：仅显示已创建的猫列表，点击即切换（每只猫独立保留养成进度）
 *
 * @param isSwitchMode true 表示从设置进入的"切换猫咪"模式
 */
@Composable
fun CatSelectionScreen(
    onCatCreated: () -> Unit,
    isSwitchMode: Boolean = false,
    onBack: (() -> Unit)? = null,
    viewModel: CatSelectionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 创建/切换完成后回调
    LaunchedEffect(state.isCreated) {
        if (state.isCreated) {
            onCatCreated()
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 返回按钮（切换模式）
        if (isSwitchMode && onBack != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 标题
        Text(
            text = if (isSwitchMode) "切换专注伙伴" else "选择你的专注伙伴",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isSwitchMode) "每只猫咪都有独立的养成进度，切换即恢复"
            else "它会陪你专注，因你进步而开心",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isSwitchMode) {
            // === 切换模式：已创建猫咪 + 可添加第二只 ===
            Text(
                text = "我的猫咪",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.existingCats, key = { it.id }) { cat ->
                    ExistingCatCard(
                        cat = cat,
                        isSelected = state.selectedExistingCatId == cat.id,
                        accentColor = accentColor,
                        onClick = { viewModel.selectExistingCat(cat.id) }
                    )
                }
                // 不足 2 只时显示"添加新伙伴"入口
                if (state.existingCats.size < 2) {
                    item(key = "add_new") {
                        AddNewCatCard(
                            isSelected = state.isAddingNewCat,
                            accentColor = accentColor,
                            isFirstFriendUnlocked = state.isFirstFriendUnlocked,
                            onClick = { viewModel.setAddingNewCat(!state.isAddingNewCat) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 添加第二只猫的子流程：品种 + 命名（仅成就已解锁时显示）
            if (state.isAddingNewCat && state.existingCats.size < 2 && state.isFirstFriendUnlocked) {
                AddSecondCatForm(
                    breeds = state.breeds,
                    selectedBreedId = state.selectedBreedId,
                    catName = state.catName,
                    isCreating = state.isCreating,
                    onBreedSelected = { viewModel.selectBreed(it) },
                    onNameChanged = { viewModel.updateName(it) },
                    onCreate = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.addSecondCat()
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 错误提示
            state.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = com.focusguard.app.ui.theme.ErrorText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // 确认切换按钮（仅在未进入添加流程时可用）
            if (!state.isAddingNewCat) {
                val canConfirm = !state.isCreating && state.selectedExistingCatId != null
                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.switchToCat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    enabled = canConfirm
                ) {
                    if (state.isCreating) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = "确认切换",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        } else {
            // === 首次选择模式：品种网格 + 命名 ===
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.breeds, key = { it.breedId }) { breed ->
                    BreedCard(
                        breed = breed,
                        isSelected = state.selectedBreedId == breed.breedId,
                        accentColor = accentColor,
                        onClick = { viewModel.selectBreed(breed.breedId) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 命名输入框
            val nameIsError = state.errorMessage != null && state.catName.isBlank()
            OutlinedTextField(
                value = state.catName,
                onValueChange = { viewModel.updateName(it) },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        "给猫咪起个名字",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                placeholder = { Text("例如：小橘、咪咪...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                isError = nameIsError,
                supportingText = {
                    if (nameIsError) {
                        Text("请输入猫咪名字", color = com.focusguard.app.ui.theme.ErrorText)
                    } else {
                        Text("${state.catName.length}/12", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 品种未选提示
            if (state.selectedBreedId == null) {
                Text(
                    text = "↑ 请先在上方选择一只猫咪品种",
                    color = com.focusguard.app.ui.theme.ErrorText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                state.errorMessage?.let { msg ->
                    if (state.catName.isNotBlank()) {
                        Text(
                            text = msg,
                            color = com.focusguard.app.ui.theme.ErrorText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }

            // 确认选择按钮
            val canCreate = !state.isCreating &&
                state.selectedBreedId != null &&
                state.catName.isNotBlank()
            Button(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.createCat()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                enabled = canCreate
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = "确认选择",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 已创建猫咪卡片（切换模式）
 *
 * 展示猫咪品种外观 + 用户起的名字 + 独立的好感度/投喂次数
 */
@Composable
private fun ExistingCatCard(
    cat: com.focusguard.app.data.local.entity.UserCatEntity,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "existing_cat_scale"
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) surfaceColor.copy(alpha = 0.8f) else surfaceColor,
        animationSpec = tween(durationMillis = 300),
        label = "existing_cat_container_color"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else accentColor.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 300),
        label = "existing_cat_border_color"
    )
    val hapticFeedback = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(4.dp)
            .scale(scale)
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 猫咪展示（不带视频，节省性能）
                CatCanvas(
                    breedId = cat.breedId,
                    state = "idle",
                    modifier = Modifier.size(130.dp),
                    useVideo = false
                )
                Spacer(modifier = Modifier.height(6.dp))
                // 用户起的名字（固定，不随切换改变）
                Text(
                    text = cat.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                // 该猫独立的好感度
                Text(
                    text = "好感度 ${cat.affinityLevel}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            // 选中徽章
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * "添加新伙伴"入口卡片（切换模式下，不足 2 只时显示）
 * @param isFirstFriendUnlocked "初识之友"成就是否已解锁，未解锁时显示锁定状态
 */
@Composable
private fun AddNewCatCard(
    isSelected: Boolean,
    accentColor: Color,
    isFirstFriendUnlocked: Boolean = false,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected && isFirstFriendUnlocked) 1.03f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "add_new_scale"
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val containerColor by animateColorAsState(
        targetValue = if (isSelected && isFirstFriendUnlocked) surfaceColor.copy(alpha = 0.8f) else surfaceColor,
        animationSpec = tween(durationMillis = 300),
        label = "add_new_container_color"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected && isFirstFriendUnlocked) accentColor else accentColor.copy(alpha = 0.3f),
        animationSpec = tween(durationMillis = 300),
        label = "add_new_border_color"
    )
    val hapticFeedback = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(4.dp)
            .scale(scale)
            .clickable(enabled = isFirstFriendUnlocked) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isFirstFriendUnlocked) {
                    // 已解锁：显示添加图标
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(accentColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "添加新伙伴",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "最多两只",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                } else {
                    // 未解锁：显示锁定状态和成就要求
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "添加新伙伴",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "解锁「初识之友」成就后开放",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "投喂 10 次即可解锁",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 切换模式下添加第二只猫的表单
 */
@Composable
private fun AddSecondCatForm(
    breeds: List<com.focusguard.app.data.local.entity.CatCatalogEntity>,
    selectedBreedId: String?,
    catName: String,
    isCreating: Boolean,
    onBreedSelected: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onCreate: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "选择第二只猫咪",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // 品种选择（一行两个）
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(breeds, key = { it.breedId }) { breed ->
                BreedCard(
                    breed = breed,
                    isSelected = selectedBreedId == breed.breedId,
                    accentColor = accentColor,
                    onClick = { onBreedSelected(breed.breedId) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 命名输入
        val nameIsError = catName.isBlank()
        OutlinedTextField(
            value = catName,
            onValueChange = onNameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("给它起个名字", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            placeholder = { Text("例如：小橘、咪咪...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            isError = nameIsError,
            supportingText = {
                if (nameIsError) {
                    Text("请输入猫咪名字", color = com.focusguard.app.ui.theme.ErrorText)
                } else {
                    Text("${catName.length}/12", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 确认添加按钮
        val canCreate = !isCreating && selectedBreedId != null
        Button(
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onCreate()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            enabled = canCreate
        ) {
            if (isCreating) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = "确认添加",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/**
 * 品种卡片（首次选择模式） - 使用 CatCanvas 矢量猫咪展示
 */
@Composable
private fun BreedCard(
    breed: com.focusguard.app.data.local.entity.CatCatalogEntity,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "breed_scale"
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) surfaceColor.copy(alpha = 0.8f) else surfaceColor,
        animationSpec = tween(durationMillis = 300),
        label = "breed_container_color"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else accentColor.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 300),
        label = "breed_border_color"
    )
    val hapticFeedback = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(4.dp)
            .scale(scale)
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CatCanvas(
                    breedId = breed.breedId,
                    state = "idle",
                    modifier = Modifier.size(130.dp),
                    useVideo = false
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = breed.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = breed.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 16.sp
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
