# Nothing Island: Schutzregeln für den R8-Release-Build.
# Activities, Services und Receiver aus dem Manifest hält AGP automatisch.

# Hidden-API-Umgehung (nutzt Reflection/Unsafe)
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

# Stacktraces lesbar halten
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Parcelable/Enums, die per Namen gelesen werden könnten
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-keepclassmembers class * implements android.os.Parcelable { public static final ** CREATOR; }

# OkHttp (WebSocket zu Creality): optionale TLS-Anbieter
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
