# 默认保留规则。当前 release 未开启 minify，此文件仅作占位与后续准备。
-keep class com.enanke.awardledger.** { *; }
# kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.enanke.awardledger.** {
    *** Companion;
}
-keepclasseswithmembers class com.enanke.awardledger.** {
    kotlinx.serialization.KSerializer serializer(...);
}
