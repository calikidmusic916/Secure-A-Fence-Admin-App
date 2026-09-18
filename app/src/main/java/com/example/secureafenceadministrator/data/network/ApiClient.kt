package com.example.secureafenceadministrator.data.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Live online backend server on Render connected to Supabase
    private const val BASE_URL = "https://secure-a-fence-backend.onrender.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val safeDoubleAdapter = object : TypeAdapter<Double>() {
        override fun write(out: JsonWriter, value: Double?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value)
            }
        }

        override fun read(reader: JsonReader): Double {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                return 0.0
            }
            if (reader.peek() == JsonToken.STRING) {
                val str = reader.nextString().trim()
                if (str.isEmpty() || str.equals("null", ignoreCase = true)) {
                    return 0.0
                }
                return str.toDoubleOrNull() ?: 0.0
            }
            if (reader.peek() == JsonToken.NUMBER) {
                return reader.nextDouble()
            }
            if (reader.peek() == JsonToken.BOOLEAN) {
                return if (reader.nextBoolean()) 1.0 else 0.0
            }
            reader.skipValue()
            return 0.0
        }
    }

    private val safeNullableDoubleAdapter = object : TypeAdapter<Double?>() {
        override fun write(out: JsonWriter, value: Double?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value)
            }
        }

        override fun read(reader: JsonReader): Double? {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                return null
            }
            if (reader.peek() == JsonToken.STRING) {
                val str = reader.nextString().trim()
                if (str.isEmpty() || str.equals("null", ignoreCase = true)) {
                    return null
                }
                return str.toDoubleOrNull()
            }
            if (reader.peek() == JsonToken.NUMBER) {
                return reader.nextDouble()
            }
            if (reader.peek() == JsonToken.BOOLEAN) {
                return if (reader.nextBoolean()) 1.0 else 0.0
            }
            reader.skipValue()
            return null
        }
    }

    private val safeIntAdapter = object : TypeAdapter<Int>() {
        override fun write(out: JsonWriter, value: Int?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value)
            }
        }

        override fun read(reader: JsonReader): Int {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                return 0
            }
            if (reader.peek() == JsonToken.STRING) {
                val str = reader.nextString().trim()
                if (str.isEmpty() || str.equals("null", ignoreCase = true)) {
                    return 0
                }
                return str.toIntOrNull() ?: str.toDoubleOrNull()?.toInt() ?: 0
            }
            if (reader.peek() == JsonToken.NUMBER) {
                return reader.nextInt()
            }
            if (reader.peek() == JsonToken.BOOLEAN) {
                return if (reader.nextBoolean()) 1 else 0
            }
            reader.skipValue()
            return 0
        }
    }

    private val safeNullableIntAdapter = object : TypeAdapter<Int?>() {
        override fun write(out: JsonWriter, value: Int?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value)
            }
        }

        override fun read(reader: JsonReader): Int? {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                return null
            }
            if (reader.peek() == JsonToken.STRING) {
                val str = reader.nextString().trim()
                if (str.isEmpty() || str.equals("null", ignoreCase = true)) {
                    return null
                }
                return str.toIntOrNull() ?: str.toDoubleOrNull()?.toInt()
            }
            if (reader.peek() == JsonToken.NUMBER) {
                return reader.nextInt()
            }
            if (reader.peek() == JsonToken.BOOLEAN) {
                return if (reader.nextBoolean()) 1 else 0
            }
            reader.skipValue()
            return null
        }
    }

    private val safeBooleanAdapter = object : TypeAdapter<Boolean>() {
        override fun write(out: JsonWriter, value: Boolean?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value)
            }
        }

        override fun read(reader: JsonReader): Boolean {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                return false
            }
            if (reader.peek() == JsonToken.STRING) {
                val str = reader.nextString().trim()
                return str.equals("true", ignoreCase = true) || str == "1"
            }
            if (reader.peek() == JsonToken.BOOLEAN) {
                return reader.nextBoolean()
            }
            if (reader.peek() == JsonToken.NUMBER) {
                return reader.nextInt() != 0
            }
            reader.skipValue()
            return false
        }
    }

    private val safeStringAdapter = object : TypeAdapter<String>() {
        override fun write(out: JsonWriter, value: String?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value)
            }
        }

        override fun read(reader: JsonReader): String {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                return ""
            }
            if (reader.peek() == JsonToken.BOOLEAN) {
                return reader.nextBoolean().toString()
            }
            if (reader.peek() == JsonToken.NUMBER) {
                return reader.nextString()
            }
            return reader.nextString().orEmpty()
        }
    }

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Double::class.javaPrimitiveType, safeDoubleAdapter)
        .registerTypeAdapter(Double::class.javaObjectType, safeNullableDoubleAdapter)
        .registerTypeAdapter(Int::class.javaPrimitiveType, safeIntAdapter)
        .registerTypeAdapter(Int::class.javaObjectType, safeNullableIntAdapter)
        .registerTypeAdapter(Boolean::class.javaPrimitiveType, safeBooleanAdapter)
        .registerTypeAdapter(Boolean::class.javaObjectType, safeBooleanAdapter)
        .registerTypeAdapter(String::class.java, safeStringAdapter)
        .setLenient()
        .create()

    val instance: AdminApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AdminApiService::class.java)
    }
}
