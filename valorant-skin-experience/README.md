# 발로란트 스킨 체험 (개인용) — Unreal Engine

발로란트 스킨을 **직접 감상하고, 쏴보고, 킬 사운드와 마무리 모션(피니셔)까지 미리 체험**하기
위한 개인용 언리얼 엔진 프로젝트 뼈대와 제작 가이드입니다.

> ⚠️ **개인 소장/감상 전용. 배포 금지.**
> 추출한 발로란트 에셋(모델·사운드·이펙트·영상)은 Riot Games의 저작권 자산입니다.
> 본인 PC에서 본인이 소장한 게임 파일을 개인적으로 보는 용도만 전제하며,
> 웹 공개·업로드·재배포는 저작권 및 Riot 이용약관 위반입니다.
> 추출 에셋은 git에 커밋하지 마세요 (`.gitignore`로 차단됨).

---

## 이게 뭐고, 뭘 담고 있나

블루프린트(`.uasset`)는 바이너리라 저장소로 직접 줄 수 없어서, **에디터에서 그대로 조립할 수
있는 "프로젝트 뼈대 + 설계도"** 형태로 제공합니다:

```
valorant-skin-experience/
├─ README.md                      ← (이 파일)
├─ UnrealProject/
│  ├─ ValorantSkinExperience.uproject   ← UE 4.27 블루프린트 전용 프로젝트
│  ├─ Config/
│  │  ├─ DefaultInput.ini         ← 입력 매핑(Fire/Reload/Inspect/스킨전환/피니셔 등) 완비
│  │  ├─ DefaultEngine.ini        ← 시작맵/게임모드/렌더 설정
│  │  └─ DefaultGame.ini
│  └─ Content/Data/
│     ├─ SkinTable.csv            ← 스킨 DataTable 템플릿 (임포트용)
│     └─ README_DataTable.md      ← FSkinData 구조체 + 임포트 방법
└─ docs/
   ├─ 01_에셋_추출_가이드.md       ← 발로란트 → UE 추출 (모델/애니/사운드/VFX/피니셔)
   ├─ 02_블루프린트_제작_가이드.md  ← BP 5개 노드 단위 설계도 (핵심)
   └─ 03_피니셔_구현.md            ← 마무리 모션 3가지 품질 레벨 + 영상 폴백
```

## 시작하는 법 (순서대로)

1. **UnrealProject 폴더를 UE 4.27로 열기** — `.uproject` 더블클릭. (BP 없이 열림)
2. **에셋 추출** → `docs/01_에셋_추출_가이드.md`
   - ValorantPorting으로 무기 메시/스킨/애니, FModel+vgmstream으로 사운드
3. **DataTable 준비** → `Content/Data/README_DataTable.md`
   - `FSkinData` 구조체 만들고 `SkinTable.csv`를 `DT_SkinTable`로 임포트
4. **블루프린트 조립** → `docs/02_블루프린트_제작_가이드.md`
   - `BP_WeaponActor`, `BP_TargetDummy`, `BP_ExperiencePawn`, `PC_Experience`, `GM_SkinExperience`
5. **사격장 레벨**(`L_ShootingRange`) 만들고 더미 배치 → 플레이

## 조작 (기본값, DefaultInput.ini)

| 키 | 동작 |
|---|---|
| 좌클릭 | 발사 (+ 발사음/총구화염) |
| R | 장전 |
| F | 감상(Inspect) |
| E / Q | 다음 / 이전 스킨 |
| **X** | **앞 더미에 피니셔 강제 재생 (마무리 모션 감상)** |
| T | 더미 리셋 |
| Tab | UI/커서 토글 |
| WASD / 마우스 | 이동 / 시점 |

## 난이도/기대치 (솔직하게)

| 기능 | 난이도 | 상태 |
|---|---|---|
| 스킨 3D 감상 | ★★☆ | 잘 됨 |
| 발사/장전/감상 애니 | ★★☆ | 잘 됨 |
| 발사음 / 킬 사운드 | ★★★ | Wwise 변환만 넘으면 됨 |
| 마무리 모션 (메시+애니+사운드) | ★★★★ | 됨 |
| 마무리 모션 이펙트(Niagara) | ★★★★★ | **완벽 재현 어려움** → 간소화/영상 폴백 |

**핵심 요약:** "쏴서 죽이면 그 스킨의 킬사운드가 나고, 전용 마무리 모션이 재생된다"는
체험은 만들 수 있습니다. 다만 피니셔의 **화려한 파티클 이펙트**는 언리얼 Niagara라
그대로 옮겨오기 어려워, 되는 만큼 재구성하거나 공식 프리뷰 영상으로 대체하는 게 현실적입니다.
