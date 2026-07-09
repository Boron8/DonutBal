package me.creeper.client.mixin;

import me.creeper.client.DonutBalClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(PlayerEntity.class)
public abstract class EntityPlayerRendererMixin {
    @Shadow public abstract Text getName();

    @Inject(
            method = "getDisplayName",
            at = @At("RETURN"),
            cancellable = true
    )
    private void getDisplayName(CallbackInfoReturnable<Text> cir) {
        Text original = cir.getReturnValue();

        if (DonutBalClient.balances.get(this.getName().getString()) != null) {
            long bal = DonutBalClient.balances.get(this.getName().getString()).bal();
            String formatted = original.getString() + " §b" + DonutBalClient.formatBalance(bal) + "§r";
            cir.setReturnValue(Text.literal(formatted));
        }
    }
}
