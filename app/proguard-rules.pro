-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

-keep,includedescriptorclasses class io.github.mimai114514.chemeilai.**$$serializer { *; }
-keepclassmembers class io.github.mimai114514.chemeilai.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.mimai114514.chemeilai.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
