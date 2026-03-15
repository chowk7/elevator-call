package com.elevator.webhook

import java.net.URL
import java.net.HttpURLConnection

object ElevatorAPIClient {
    private const val CALL_URL = "http://10.2.1.11/seoulapp/ezon_v2/common/elevator.do?method=ezon_v2.common.elevator.CallXML&flag=down&hogi=10"
    private const val STATUS_URL = "http://10.2.1.11/seoulapp/ezon_v2/common/elevator.do?method=ezon_v2.common.elevator.StateXML&hogi1=10"
    private const val TIMEOUT_MS = 10000

    fun callElevator(): Result<String> {
        return try {
            val response = makeRequest(CALL_URL)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getElevatorStatus(): Result<String> {
        return try {
            val response = makeRequest(STATUS_URL)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun makeRequest(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.requestMethod = "GET"

        return try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }
            } else {
                throw Exception("HTTP Error: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
}
