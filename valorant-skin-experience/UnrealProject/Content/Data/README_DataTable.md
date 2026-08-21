# SkinTable.csv → DataTable 임포트 방법

이 CSV는 UE에서 **DataTable 에셋으로 임포트**하기 위한 것입니다. 그냥 두면 데이터가 아니라,
아래 순서로 임포트해야 블루프린트에서 스킨 데이터를 읽을 수 있습니다.

## 1단계: Struct 먼저 만들기 (필수)

DataTable은 "행 구조체(Row Struct)"가 있어야 임포트됩니다.

1. 콘텐츠 브라우저 우클릭 → **Blueprints > Structure** → 이름 `FSkinData` (에셋명 `S_SkinData` 권장)
2. 아래 변수들을 **CSV 헤더와 똑같은 이름/순서**로 추가:

| 변수 이름 | 타입 | 비고 |
|---|---|---|
| DisplayName | Text | 화면에 표시할 스킨 이름 |
| WeaponType | Name (또는 Enum) | 무기 종류 (Vandal, Phantom...) |
| WeaponMesh | Skeletal Mesh (Soft Reference) | 무기 스켈레탈 메시 |
| MaterialOverride | Material Interface (Soft) | 없으면 None |
| FireAnim | Anim Montage (Soft) | 발사 몽타주 |
| ReloadAnim | Anim Montage (Soft) | 장전 몽타주 |
| InspectAnim | Anim Montage (Soft) | 감상 몽타주 |
| FireSound | Sound Base (Soft) | 발사음 |
| ReloadSound | Sound Base (Soft) | 장전음 |
| EquipSound | Sound Base (Soft) | 집어들기 소리 |
| KillSound | Sound Base (Soft) | 킬 사운드 |
| MuzzleFlashVFX | Niagara System (Soft) | 총구 화염 |
| FinisherMesh | Skeletal Mesh (Soft) | 피니셔 오브젝트 메시 |
| FinisherAnim | Anim Montage (Soft) | 피니셔 애니메이션 |
| FinisherSound | Sound Base (Soft) | 피니셔 사운드 |
| FinisherVFX | Niagara System (Soft) | 피니셔 이펙트 |
| FinisherPreviewVideo | String | (선택) 공식 프리뷰 영상 경로/URL — VFX 재현 대체용 |

> **Soft Reference**를 쓰는 이유: 스킨이 많아지면 전부 메모리에 올리지 않고, 선택했을 때만
> 비동기 로드(Async Load Asset)해서 성능/메모리를 아끼려는 것. Hard Reference로 해도 동작은 합니다.

## 2단계: CSV 임포트

1. 콘텐츠 브라우저에 `SkinTable.csv`를 드래그
2. 임포트 옵션 창에서 **Import As: DataTable**, **Row Type: FSkinData** 선택
3. 생성된 DataTable 이름을 `DT_SkinTable`로

## 3단계: 경로 채우기

CSV의 경로들은 **예시 placeholder**입니다. 실제로는:
1. ValorantPorting/FModel로 에셋을 `Content/Valorant/...`에 임포트한 뒤
2. 각 에셋 우클릭 → **Copy Reference** 로 실제 경로를 복사
3. DataTable을 에디터에서 열어 각 셀에 붙여넣기 (또는 CSV를 고쳐 재임포트)

없는 값은 `None`으로 두면 블루프린트에서 "유효성 검사 후 스킵" 하도록 처리합니다
(예: 피니셔 없는 스킨은 FinisherMesh=None → 킬 시 기본 사망 처리).
