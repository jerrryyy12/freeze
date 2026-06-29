package com.chameleon.client;

import com.chameleon.ChameleonMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 감염 모드: 숨는 사람에게는 다른 숨는 사람이 보이지 않는다(미리 서로 잡는 것 방지).
 * 술래에게는 전원 보인다. 역할 판별은 술래 팀(camo_seeker) 소속으로 한다(스코어보드는 클라에 동기화됨).
 * 가장 먼저(HIGHEST) 실행해 취소하면, 이모트 렌더 등 다른 렌더 처리도 건너뛴다.
 */
@Mod.EventBusSubscriber(modid = ChameleonMod.MOD_ID, value = Dist.CLIENT)
public final class HiderVisibility {

    private static final String SEEKER_TEAM = "camo_seeker";

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!CamoEditState.infectionMode) return;
        if (CamoEditState.localRole != 1) return;          // 나는 숨는 사람일 때만 적용
        int ph = CamoEditState.phase;
        if (ph < 1 || ph > 3) return;                       // 준비/숨기/찾기 동안만(공개·종료 땐 전원 표시)
        Minecraft mc = Minecraft.getInstance();
        Player rendered = event.getEntity();
        if (rendered == mc.player) return;                  // 나 자신은 보임
        if (isSeeker(rendered)) return;                     // 술래는 보임
        event.setCanceled(true);                            // 다른 숨는 사람은 숨김
    }

    private static boolean isSeeker(Player p) {
        Team t = p.getTeam();
        return t != null && SEEKER_TEAM.equals(t.getName());
    }
}
