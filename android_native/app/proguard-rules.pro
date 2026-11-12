# Proguard rules for Retrofit & Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class com.smart.msrlab.data.model.** { *; }
