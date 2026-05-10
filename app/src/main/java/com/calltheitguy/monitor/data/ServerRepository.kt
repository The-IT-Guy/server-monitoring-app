package com.calltheitguy.monitor.data

import kotlinx.coroutines.flow.Flow

class ServerRepository(
    private val serverDao: ServerDao,
) {

    fun observeServers(): Flow<List<ServerEntity>> {
        return serverDao.observeAll()
    }

    suspend fun getAll(): List<ServerEntity> {
        return serverDao.getAll()
    }

    suspend fun getEnabled(): List<ServerEntity> {
        return serverDao.getEnabled()
    }

    suspend fun getById(id: Int): ServerEntity? {
        return serverDao.getById(id)
    }

    suspend fun addServer(server: ServerEntity): Int {
        return serverDao.insert(server).toInt()
    }

    suspend fun updateServer(server: ServerEntity) {
        serverDao.update(server)
    }

    suspend fun deleteServer(server: ServerEntity) {
        serverDao.delete(server)
    }

    suspend fun deleteById(id: Int) {
        serverDao.deleteById(id)
    }
}
