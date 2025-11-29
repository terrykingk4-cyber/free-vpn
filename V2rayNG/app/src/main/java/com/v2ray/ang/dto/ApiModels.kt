package com.v2ray.ang.dto

import com.google.gson.annotations.SerializedName

data class HandshakeRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("app_version") val appVersion: String
)

data class HandshakeResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: HandshakeData?
)

data class HandshakeData(
    @SerializedName("text") val text: String?,
    @SerializedName("configs") val configs: String?,
    @SerializedName("update_needed") val updateNeeded: Boolean,
    @SerializedName("force_update") val forceUpdate: Boolean,
    @SerializedName("server_version") val serverVersion: String?
)