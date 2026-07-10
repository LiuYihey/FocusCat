package com.focusguard.app.data.repository

import com.focusguard.app.data.local.dao.BlockedAppDao
import com.focusguard.app.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

/**
 * 被拦截应用仓库实现类
 * 封装 BlockedAppDao，提供应用层使用的数据访问方法
 * @param blockedAppDao 被拦截应用 DAO
 */
class AppRepositoryImpl(
    private val blockedAppDao: BlockedAppDao
) {

    /**
     * 添加一个被拦截应用
     * @param packageName 应用包名
     * @param appName 应用名称
     * @param iconBytes 应用图标字节数组（可为空）
     */
    suspend fun addBlockedApp(
        packageName: String,
        appName: String,
        iconBytes: ByteArray?
    ) {
        val entity = BlockedAppEntity(
            packageName = packageName,
            appName = appName,
            iconBytes = iconBytes,
            addedAt = System.currentTimeMillis(),
            isActive = true,
            blockCount = 0
        )
        blockedAppDao.insert(entity)
    }

    /**
     * 移除一个被拦截应用
     * @param entity 待移除的应用实体
     */
    suspend fun removeBlockedApp(entity: BlockedAppEntity) {
        blockedAppDao.delete(entity)
    }

    /**
     * 获取所有处于激活状态的被拦截应用列表
     * @return 激活状态的被拦截应用列表
     */
    suspend fun getActiveBlockedApps(): List<BlockedAppEntity> {
        return blockedAppDao.getActiveBlockedApps()
    }

    /**
     * 获取所有被拦截应用列表（以 Flow 形式返回，可观察数据变化）
     * @return 所有被拦截应用的 Flow
     */
    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>> {
        return blockedAppDao.getAllBlockedApps()
    }

    /**
     * 将指定应用的拦截次数自增 1
     * @param packageName 应用包名
     */
    suspend fun incrementBlockCount(packageName: String) {
        blockedAppDao.updateBlockCount(packageName)
    }

    /**
     * 设置应用的激活状态（启用/禁用拦截）
     * @param packageName 应用包名
     * @param active true=启用拦截，false=暂停拦截（保留记录但不拦截）
     */
    suspend fun setAppActive(packageName: String, active: Boolean) {
        blockedAppDao.setActive(packageName, active)
    }
}
