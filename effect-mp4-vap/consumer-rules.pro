# effect-mp4-vap consumer rules.
#
# VAP includes native/rendering code, so keep its package names stable. The
# rule is scoped to VAP only and is applied only when this module is included.

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

-keep,allowshrinking,allowoptimization public class com.chat.effect.mp4.vap.VapMp4EffectPlayer {
    public <init>();
    public *;
}

-keep class com.tencent.qgame.animplayer.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class com.tencent.qgame.animplayer.** {
    native <methods>;
}
