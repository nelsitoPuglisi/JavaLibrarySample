# How to use Embrace from a `java-library` module

Embrace Android SDK is strongly dependent on the Android SDK itself. This creates a problem for projects that want to instrument a `java-library` module. The Embrace SDK can't be added as a dependency.

I created this repo as a guide about capturing okhttp network requests when okhttp is a dependency of a `java-library` module instead of an `android-library`.

In this example, I've isolated Embrace from the [EmbraceOkHttp3NetworkInterceptor](https://github.com/embrace-io/embrace-android-sdk/blob/6.13.0/embrace-android-okhttp3/src/main/java/io/embrace/android/embracesdk/okhttp3/EmbraceOkHttp3NetworkInterceptor.kt#L107) class.

## How to use it

1. Add all the classes prefixed with `Embrace` in the [lib](https://github.com/nelsitoPuglisi/JavaLibrarySample/tree/main/lib/src/main/java/io/embrace/lib) folder to your `java-library` module.

> This will create an abstraction between the actual Embrace SDK and Embrace Okhttp Interceptors.
   
2. Add the interceptors to the okhttp client as shown [here](https://github.com/nelsitoPuglisi/JavaLibrarySample/blob/0c514da0c0653205ac4d6a9b34cbdb919ed84186/lib/src/main/java/io/embrace/lib/Networking.kt#L14)

 ```
   val client = OkHttpClient.Builder()
            .addInterceptor(EmbraceOkHttp3ApplicationInterceptor(embraceAbrastraction))
            .addInterceptor(EmbraceOkHttp3NetworkInterceptor(embraceAbrastraction))
            .build()
```

3. Add the [EmbraceIOC](https://github.com/nelsitoPuglisi/JavaLibrarySample/blob/main/app/src/main/java/io/embrace/javalibrarysample/EmbraceNetworkingInversionOfControl.kt) class to the same module that calls `Embrace.getInstance().start(this)`.

> This class will have the proper Embrace API calls to record a network request.

4. Finally, whenever you make a network request in your code, you can pass the abstraction. In this example, it is a parameter.

```
private val emb = EmbraceNetworkingInversionOfControl(Embrace.getInstance())
Networking().myAPI(
                    "https://httpbin.org/get",
                    emb.embraceAbrastraction
                )
```
