package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = false)
data class OFFProductResponse(
    @Json(name = "product") val product: OFFProduct? = null,
    @Json(name = "status") val status: Int = 0
)

@JsonClass(generateAdapter = false)
data class OFFSearchResponse(
    @Json(name = "products") val products: List<OFFProduct>? = null
)

@JsonClass(generateAdapter = false)
data class OFFProduct(
    @Json(name = "product_name") val productName: String? = null,
    @Json(name = "brands") val brands: String? = null,
    @Json(name = "code") val code: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "nutriments") val nutriments: OFFNutriments? = null
)

@JsonClass(generateAdapter = false)
data class OFFNutriments(
    @Json(name = "energy-kcal_100g") val energyKcal100g: Float? = null,
    @Json(name = "proteins_100g") val proteins100g: Float? = null,
    @Json(name = "carbohydrates_100g") val carbohydrates100g: Float? = null,
    @Json(name = "fat_100g") val fat100g: Float? = null
)

interface OpenFoodFactsApiService {
    @GET("api/v0/product/{barcode}.json")
    suspend fun getProductByBarcode(@Path("barcode") barcode: String): OFFProductResponse

    @GET("cgi/search.pl?search_simple=1&action=process&json=1&page_size=15&fields=product_name,product_name_pl,generic_name,generic_name_pl,brands,code,image_url,image_front_small_url,image_front_url,nutriments")
    suspend fun searchProducts(@Query("search_terms") query: String): OFFSearchResponse
}

internal interface OpenFoodFactsLiveSearchService {
    @GET("https://search.openfoodfacts.org/search?page_size=15&fields=product_name,product_name_pl,generic_name,generic_name_pl,brands,code,image_url,image_front_small_url,image_front_url,nutriments")
    suspend fun searchModern(@Query("q") query: String): OFFSearchResponse

    @GET("https://pl.openfoodfacts.org/cgi/search.pl?search_simple=1&action=process&json=1&page_size=15&fields=product_name,product_name_pl,generic_name,generic_name_pl,brands,code,image_url,image_front_small_url,image_front_url,nutriments")
    suspend fun searchPl(@Query("search_terms") query: String): OFFSearchResponse
}

internal object OpenFoodFactsMoshiAdapters {
    private fun readFlexibleFloat(reader: JsonReader): Float? {
        return when (reader.peek()) {
            JsonReader.Token.NUMBER -> {
                try {
                    reader.nextDouble().toFloat().takeIf { !it.isNaN() && !it.isInfinite() && it >= 0f }
                } catch (_: Exception) {
                    reader.skipValue()
                    null
                }
            }
            JsonReader.Token.STRING -> {
                val raw = reader.nextString().trim().replace(',', '.')
                raw.toFloatOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0f }
            }
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                null
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    private fun readFlexibleString(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> reader.nextString().trim().takeIf { it.isNotEmpty() }
            JsonReader.Token.NUMBER -> reader.nextString().trim().takeIf { it.isNotEmpty() }
            JsonReader.Token.BEGIN_ARRAY -> {
                val items = mutableListOf<String>()
                reader.beginArray()
                while (reader.hasNext()) {
                    if (reader.peek() == JsonReader.Token.STRING) {
                        val s = reader.nextString().trim()
                        if (s.isNotEmpty()) items.add(s)
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endArray()
                items.joinToString(", ").takeIf { it.isNotEmpty() }
            }
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                null
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    private fun readNutriments(reader: JsonReader): OFFNutriments? {
        if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull<Unit>()
            return null
        }
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }

        var energyKcal100g: Float? = null
        var energyKj100g: Float? = null
        var genericEnergyKj100g: Float? = null
        var proteins100g: Float? = null
        var carbohydrates100g: Float? = null
        var fat100g: Float? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "energy-kcal_100g" -> {
                    val v = readFlexibleFloat(reader)
                    if (energyKcal100g == null && v != null) energyKcal100g = v
                }
                "energy-kj_100g" -> {
                    val v = readFlexibleFloat(reader)
                    if (energyKj100g == null && v != null) energyKj100g = v
                }
                "energy_100g" -> genericEnergyKj100g = readFlexibleFloat(reader)
                "proteins_100g" -> proteins100g = readFlexibleFloat(reader)
                "carbohydrates_100g" -> carbohydrates100g = readFlexibleFloat(reader)
                "fat_100g" -> fat100g = readFlexibleFloat(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        // *_value may describe a serving. Only normalized *_100g fields are safe here.
        val resolvedKcal = energyKcal100g ?: (energyKj100g ?: genericEnergyKj100g)?.let { it / 4.184f }
        return OFFNutriments(
            energyKcal100g = resolvedKcal,
            proteins100g = proteins100g,
            carbohydrates100g = carbohydrates100g,
            fat100g = fat100g
        )
    }

    internal fun readProduct(reader: JsonReader): OFFProduct? {
        if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull<Unit>()
            return null
        }
        if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }

        var productName: String? = null
        var productNamePl: String? = null
        var genericName: String? = null
        var brands: String? = null
        var code: String? = null
        var imageUrl: String? = null
        var imageFrontSmallUrl: String? = null
        var imageFrontUrl: String? = null
        var nutriments: OFFNutriments? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "product_name" -> productName = readFlexibleString(reader)
                "product_name_pl" -> productNamePl = readFlexibleString(reader)
                "generic_name_pl", "generic_name" -> {
                    val v = readFlexibleString(reader)
                    if (genericName == null && v != null) genericName = v
                }
                "brands" -> brands = readFlexibleString(reader)
                "code", "_id" -> {
                    val v = readFlexibleString(reader)
                    if (code == null && v != null) code = v
                }
                "image_url" -> imageUrl = readFlexibleString(reader)
                "image_front_small_url" -> imageFrontSmallUrl = readFlexibleString(reader)
                "image_front_url" -> imageFrontUrl = readFlexibleString(reader)
                "nutriments" -> nutriments = readNutriments(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val resolvedName = productNamePl?.takeIf { it.isNotBlank() }
            ?: productName?.takeIf { it.isNotBlank() }
            ?: genericName?.takeIf { it.isNotBlank() }
        val resolvedImage = imageFrontUrl?.takeIf { it.isNotBlank() }
            ?: imageUrl?.takeIf { it.isNotBlank() }
            ?: imageFrontSmallUrl?.takeIf { it.isNotBlank() }

        return OFFProduct(
            productName = resolvedName,
            brands = brands,
            code = code,
            imageUrl = resolvedImage,
            nutriments = nutriments
        )
    }

    val factory: JsonAdapter.Factory = object : JsonAdapter.Factory {
        override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
            if (annotations.isNotEmpty()) return null
            return when (type) {
                OFFProductResponse::class.java -> object : JsonAdapter<OFFProductResponse>() {
                    override fun fromJson(reader: JsonReader): OFFProductResponse {
                        var product: OFFProduct? = null
                        var status = 0
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "product" -> product = readProduct(reader)
                                "status" -> {
                                    status = when (reader.peek()) {
                                        JsonReader.Token.NUMBER -> reader.nextInt()
                                        JsonReader.Token.STRING -> reader.nextString().toIntOrNull() ?: 0
                                        else -> {
                                            reader.skipValue()
                                            0
                                        }
                                    }
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        return OFFProductResponse(product = product, status = status)
                    }

                    override fun toJson(writer: JsonWriter, value: OFFProductResponse?) {
                        throw UnsupportedOperationException()
                    }
                }

                OFFSearchResponse::class.java -> object : JsonAdapter<OFFSearchResponse>() {
                    override fun fromJson(reader: JsonReader): OFFSearchResponse {
                        val products = mutableListOf<OFFProduct>()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "products", "hits" -> {
                                    if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                        reader.beginArray()
                                        while (reader.hasNext()) {
                                            readProduct(reader)?.let { p ->
                                                if (!p.productName.isNullOrBlank()) {
                                                    products.add(p)
                                                }
                                            }
                                        }
                                        reader.endArray()
                                    } else {
                                        reader.skipValue()
                                    }
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        return OFFSearchResponse(products = products)
                    }

                    override fun toJson(writer: JsonWriter, value: OFFSearchResponse?) {
                        throw UnsupportedOperationException()
                    }
                }

                else -> null
            }
        }
    }
}

object OpenFoodFactsClient {
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(OpenFoodFactsMoshiAdapters.factory)
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "MangoFitAndroid/1.0 (https://mangofit.app)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val defaultService: OpenFoodFactsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenFoodFactsApiService::class.java)
    }

    private val liveSearchService: OpenFoodFactsLiveSearchService by lazy {
        Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenFoodFactsLiveSearchService::class.java)
    }

    @Volatile
    var service: OpenFoodFactsApiService = defaultService

    fun resetToDefault() {
        service = defaultService
    }

    /** One HTTP request for rate-limited batch repair; never hides extra fallback requests. */
    suspend fun searchProductsForNutritionRepair(query: String): OFFSearchResponse =
        if (service !== defaultService) service.searchProducts(query) else liveSearchService.searchModern(query)

    suspend fun searchProductsWithFallback(query: String): OFFSearchResponse {
        val currentService = service
        if (currentService !== defaultService) {
            return currentService.searchProducts(query)
        }
        return try {
            val modern = liveSearchService.searchModern(query)
            if (!modern.products.isNullOrEmpty()) {
                modern
            } else {
                liveSearchService.searchPl(query)
            }
        } catch (_: Exception) {
            try {
                liveSearchService.searchPl(query)
            } catch (_: Exception) {
                currentService.searchProducts(query)
            }
        }
    }

    fun parseSearchResponseJson(json: String): OFFSearchResponse {
        val adapter = moshi.adapter(OFFSearchResponse::class.java)
        return adapter.fromJson(json) ?: OFFSearchResponse(emptyList())
    }

    fun parseProductResponseJson(json: String): OFFProductResponse {
        val adapter = moshi.adapter(OFFProductResponse::class.java)
        return adapter.fromJson(json) ?: OFFProductResponse(null, 0)
    }
}
