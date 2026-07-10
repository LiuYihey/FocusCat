package com.focusguard.app.ui.cat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.local.entity.CatCatalogEntity
import com.focusguard.app.data.local.entity.UserCatEntity
import com.focusguard.app.data.repository.CatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 猫咪选择界面的 UI 状态
 */
data class CatSelectionUiState(
    /** 所有可选品种（首次选择用） */
    val breeds: List<CatCatalogEntity> = emptyList(),
    /** 用户已创建的所有猫咪（切换模式用） */
    val existingCats: List<UserCatEntity> = emptyList(),
    /** 当前选中的品种 ID（首次选择 / 切换模式下添加第二只用） */
    val selectedBreedId: String? = null,
    /** 当前选中的已创建猫咪 ID（切换模式用） */
    val selectedExistingCatId: Int? = null,
    /** 用户输入的猫名（首次选择 / 切换模式下添加第二只用） */
    val catName: String = "",
    /** 切换模式下是否处于“添加第二只猫”的子流程 */
    val isAddingNewCat: Boolean = false,
    /** 是否正在创建/切换 */
    val isCreating: Boolean = false,
    /** 是否创建/切换完成 */
    val isCreated: Boolean = false,
    /** 错误提示 */
    val errorMessage: String? = null
)

/**
 * 猫咪选择 ViewModel
 *
 * 简化设计（仅 2 个品种：布偶 + 橘猫）：
 * - 首次启动：选品种 + 起名 → 创建第一只猫
 * - 切换模式：显示已创建的猫列表，点击即切换；若只有 1 只，可添加第二只（上限 2 只）
 */
@HiltViewModel
class CatSelectionViewModel @Inject constructor(
    private val catRepository: CatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CatSelectionUiState())
    val state: StateFlow<CatSelectionUiState> = _state.asStateFlow()

    init {
        loadBreeds()
        loadExistingCats()
    }

    /** 观察所有品种（Flow），种子数据插入后自动更新 */
    private fun loadBreeds() {
        viewModelScope.launch {
            catRepository.observeAllBreeds().collect { breeds ->
                _state.update { it.copy(breeds = breeds) }
            }
        }
    }

    /** 观察所有已创建的猫咪（Flow），用于切换模式展示 */
    private fun loadExistingCats() {
        viewModelScope.launch {
            catRepository.observeAllCats().collect { cats ->
                _state.update { it.copy(existingCats = cats) }
            }
        }
    }

    /** 选择品种（首次选择模式） */
    fun selectBreed(breedId: String) {
        _state.update { it.copy(selectedBreedId = breedId, errorMessage = null) }
    }

    /** 选择已创建的猫咪（切换模式） */
    fun selectExistingCat(catId: Int) {
        _state.update {
            it.copy(
                selectedExistingCatId = catId,
                isAddingNewCat = false,
                selectedBreedId = null,
                catName = "",
                errorMessage = null
            )
        }
    }

    /** 进入/退出“添加第二只猫”子流程 */
    fun setAddingNewCat(adding: Boolean) {
        _state.update {
            it.copy(
                isAddingNewCat = adding,
                selectedExistingCatId = if (adding) null else it.selectedExistingCatId,
                selectedBreedId = null,
                catName = "",
                errorMessage = null
            )
        }
    }

    /** 更新猫名（限制最多 12 个字符） */
    fun updateName(name: String) {
        val trimmed = if (name.length > 12) name.take(12) else name
        _state.update { it.copy(catName = trimmed, errorMessage = null) }
    }

    /**
     * 创建新猫咪（首次选择）
     */
    fun createCat() {
        val currentState = _state.value
        if (currentState.selectedBreedId == null) {
            _state.update { it.copy(errorMessage = "请选择一只猫咪") }
            return
        }
        if (currentState.catName.isBlank()) {
            _state.update { it.copy(errorMessage = "请给猫咪起个名字") }
            return
        }

        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                catRepository.createCat(
                    breedId = currentState.selectedBreedId,
                    name = currentState.catName.trim()
                )
                _state.update { it.copy(isCreating = false, isCreated = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isCreating = false, errorMessage = "创建失败，请重试")
                }
            }
        }
    }

    /**
     * 切换到指定猫咪（恢复该猫的独立养成进度）
     */
    fun switchToCat() {
        val catId = _state.value.selectedExistingCatId
        if (catId == null) {
            _state.update { it.copy(errorMessage = "请选择一只猫咪") }
            return
        }
        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                catRepository.switchToCat(catId)
                _state.update { it.copy(isCreating = false, isCreated = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isCreating = false, errorMessage = "切换失败，请重试")
                }
            }
        }
    }

    /**
     * 切换模式下添加第二只猫
     * 必须先选择品种并输入名字
     */
    fun addSecondCat() {
        val currentState = _state.value
        if (currentState.existingCats.size >= 2) {
            _state.update { it.copy(errorMessage = "最多只能拥有两只猫咪") }
            return
        }
        if (currentState.selectedBreedId == null) {
            _state.update { it.copy(errorMessage = "请选择一只猫咪品种") }
            return
        }
        if (currentState.catName.isBlank()) {
            _state.update { it.copy(errorMessage = "请先给猫咪起个名字") }
            return
        }
        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                catRepository.createCat(
                    breedId = currentState.selectedBreedId,
                    name = currentState.catName.trim()
                )
                _state.update {
                    it.copy(
                        isCreating = false,
                        isCreated = true,
                        isAddingNewCat = false
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isCreating = false, errorMessage = "创建失败，请重试")
                }
            }
        }
    }
}
