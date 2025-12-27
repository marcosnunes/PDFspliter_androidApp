# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keepclassmembers class com.PDFspliter.FullscreenActivity$WebAppInterface {
   @android.webkit.JavascriptInterface
   public *;
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

# Mantém as classes principais do ML Kit e Vision para evitar que sejam removidas
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }

# Previne avisos que podem interromper o build
-dontwarn com.google.mlkit.**

# Se estiver usando a versão "Bundled" (Offline), é crucial manter os modelos e o JNI
-keep class com.google.android.gms.tasks.** { *; }

# Mantém interfaces nativas usadas pelo ML Kit
-keepclasseswithmembernames class * {
    native <methods>;
}

# (Opcional) Se tiver problemas com o 'protobuf', descomente:
# -keep class com.google.protobuf.** { *; }
