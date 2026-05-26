# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ══════════════════════════════════════════════════════════════
# Dexgram API — keep serializable response classes
# ══════════════════════════════════════════════════════════════
-keep class chat.simplex.common.views.chatlist.LoginResponse { *; }
-keep class chat.simplex.common.views.chatlist.SubscriptionResponse { *; }
-keep class chat.simplex.common.views.chatlist.SubscriptionLimits { *; }
-keep class chat.simplex.common.views.chatlist.GooglePurchaseResponse { *; }
-keep class chat.simplex.common.views.chatlist.GooglePurchaseSubscription { *; }
-keepclassmembers class chat.simplex.common.views.chatlist.LoginResponse { *; }
-keepclassmembers class chat.simplex.common.views.chatlist.SubscriptionResponse { *; }
-keepclassmembers class chat.simplex.common.views.chatlist.SubscriptionLimits { *; }
-keepclassmembers class chat.simplex.common.views.chatlist.GooglePurchaseResponse { *; }
-keepclassmembers class chat.simplex.common.views.chatlist.GooglePurchaseSubscription { *; }

# DexgramBilling / DexgramProduct / DexgramPurchaseResult
-keep class chat.simplex.common.platform.DexgramBilling { *; }
-keep class chat.simplex.common.platform.DexgramProduct { *; }
-keep class chat.simplex.common.platform.DexgramPurchaseResult { *; }

# Google Play Billing
-keep class com.android.vending.billing.** { *; }
-dontwarn com.android.billingclient.**
-keep class com.android.billingclient.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt