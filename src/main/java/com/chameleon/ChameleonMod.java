package com.chameleon;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * MECCHA CHAMELEON - 위장 숨바꼭질 모드.
 *
 * <p>1단계: 커스텀 플레이어 렌더 레이어 검증. 웅크리면(Shift) 몸이 발밑 블록 색으로
 * 위장되어 모든 클라이언트에 보인다. 위치/웅크림 상태는 마인크래프트가 이미 동기화하므로
 * 별도 패킷 없이 렌더링 파이프라인만으로 동작한다.</p>
 */
@Mod(ChameleonMod.MOD_ID)
public class ChameleonMod {
    public static final String MOD_ID = "chameleon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChameleonMod() {
        // 클라이언트 렌더링 등록은 client.ChameleonClient(@EventBusSubscriber, Dist.CLIENT)가 담당.
        LOGGER.info("Chameleon 모드 로드 완료");
    }
}
