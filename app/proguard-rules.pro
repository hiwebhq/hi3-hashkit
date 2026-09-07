# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class hi3.hashkit.** {
    *** Companion;
}
-keepclasseswithmembers class hi3.hashkit.** {
    kotlinx.serialization.KSerializer serializer(...);
}
