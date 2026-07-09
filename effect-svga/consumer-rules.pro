# effect-svga consumer rules.
#
# Keep only the module entry point and SVGAPlayer classes that may be touched
# by callbacks/reflection inside the third-party player.

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

-keep,allowshrinking,allowoptimization public class com.chat.effect.svga.SvgaEffectPlayer {
    public <init>();
    public *;
}

-keep,allowshrinking,allowoptimization class com.opensource.svgaplayer.** { *; }
