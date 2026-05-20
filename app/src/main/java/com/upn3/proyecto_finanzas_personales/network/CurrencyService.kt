package com.upn3.proyecto_finanzas_personales.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

data class CurrencyResponse(
    @SerializedName("result") val result: String? = null,
    @SerializedName("base_code") val baseCode: String? = null,
    @SerializedName("rates") val rates: Map<String, Double>? = null
)

interface CurrencyService {
    @GET("{code}")
    suspend fun getCurrencyRate(
        @Path("code") code: String
    ): CurrencyResponse
}
