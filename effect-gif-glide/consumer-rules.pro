# effect-gif-glide consumer rules.
#
# Glide already ships its own consumer rules. This module only keeps the
# public player entry point readable and lets R8 shrink unused code.

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

-keep,allowshrinking,allowoptimization public class com.chat.effect.gif.glide.GifEffectPlayer {
    public <init>();
    public *;
}
