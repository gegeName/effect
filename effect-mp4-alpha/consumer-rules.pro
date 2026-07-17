# effect-mp4-alpha consumer rules.
#
# AlphaPlayer includes rendering code and a worker player thread, so keep its
# package names stable. The rule is scoped to AlphaPlayer only and is applied
# only when this module is included.

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

-keep,allowshrinking,allowoptimization public class com.chat.effect.mp4.alpha.AlphaMp4EffectPlayer {
    public <init>();
    public *;
}

-keep class com.ss.ugc.android.alpha_player.** { *; }
