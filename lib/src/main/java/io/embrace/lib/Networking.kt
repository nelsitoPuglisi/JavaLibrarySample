package io.embrace.lib
import okhttp3.OkHttpClient
import okhttp3.Request

class Networking {

    fun myAPI(url: String, embraceAbrastraction: EmbraceAbrastraction) {
        val builder: Request.Builder = Request.Builder()
        builder.url(url)
        builder.get()
        val request = builder.build()

        val client = OkHttpClient.Builder()
            .addInterceptor(EmbraceOkHttp3ApplicationInterceptor(embraceAbrastraction))
            .addInterceptor(EmbraceOkHttp3NetworkInterceptor(embraceAbrastraction))
            .build()

        client.newCall(request).execute()
    }
}