# effect-mp4-yyeva consumer rules.
#
# YYEVA includes native/rendering code, so keep its package names stable. The
# rule is scoped to YYEVA only and is applied only when this module is included.

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

-keep,allowshrinking,allowoptimization public class com.chat.effect.mp4.yyeva.YyEvaMp4EffectPlayer {
    public <init>();
    public *;
}

-keep class com.yy.yyeva.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class com.yy.yyeva.** {
    native <methods>;
}
