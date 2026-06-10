package com.upn3.proyecto_finanzas_personales.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object CloudinaryClient {
    val api: CloudinaryApi by lazy {

        Retrofit.Builder()
            .baseUrl("https://api.cloudinary.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudinaryApi::class.java)
    }
}