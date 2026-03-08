package com.example.sharedkhatm.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit
import retrofit2.Call
import retrofit2.Response

/**
 * Konum izni yokken IP ile şehir tahmini veya varsayılan Ankara.
 * Tüm ağ çağrıları Dispatchers.IO, 2 sn timeout, retry yok, main thread bloklamaz.
 */
object LocationFallbackManager {

    private const val IP_API_BASE = "http://ip-api.com/"
    private const val TIMEOUT_MS = 2_000L
    const val DEFAULT_CITY = "Ankara"
    const val DEFAULT_LAT = 39.9334
    const val DEFAULT_LON = 32.8597
    private const val TURKEY_CODE = "TR"

    private data class IpApiResponse(
        val city: String? = null,
        val countryCode: String? = null,
        val lat: Double? = null,
        val lon: Double? = null
    )

    private interface IpApiService {
        @GET("json?fields=city,countryCode,lat,lon")
        fun getLocation(): Call<IpApiResponse>
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(IP_API_BASE)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val ipApi: IpApiService by lazy { retrofit.create(IpApiService::class.java) }

    /**
     * Dispatchers.IO üzerinde çağrılmalı. IP ile şehir dener; başarısız veya Türkiye dışı ise Ankara döner.
     * Retry yok, timeout 2 sn.
     */
    suspend fun getCityFromIpOrDefault(): CityResult = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            try {
                val response: Response<IpApiResponse> = ipApi.getLocation().execute()
                if (!response.isSuccessful) return@withTimeoutOrNull CityResult.Default
                val resp = response.body() ?: return@withTimeoutOrNull CityResult.Default
                val code = resp.countryCode?.uppercase() ?: ""
                if (code != TURKEY_CODE) return@withTimeoutOrNull CityResult.Default
                val city = resp.city?.takeIf { it.isNotBlank() } ?: return@withTimeoutOrNull CityResult.Default
                val lat = resp.lat ?: return@withTimeoutOrNull CityResult.Default
                val lon = resp.lon ?: return@withTimeoutOrNull CityResult.Default
                CityResult.FromIp(city, lat, lon)
            } catch (_: Exception) {
                CityResult.Default
            }
        }
        result ?: CityResult.Default
    }

    sealed class CityResult {
        data class FromIp(val city: String, val lat: Double, val lon: Double) : CityResult()
        object Default : CityResult() {
            val city = DEFAULT_CITY
            val lat = DEFAULT_LAT
            val lon = DEFAULT_LON
        }
    }
}
