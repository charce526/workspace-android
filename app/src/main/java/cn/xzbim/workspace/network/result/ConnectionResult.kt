package cn.xzbim.workspace.network.result

sealed class ConnectionResult {
    data class Success(val serverUrl: String, val version: String = "2.0+") : ConnectionResult()
    data class ServerError(val statusCode: Int, val message: String) : ConnectionResult()
    data class NetworkError(val message: String) : ConnectionResult()
}
