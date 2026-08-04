; 폰 검수 프로그램 설치 마법사 스크립트
; GitHub Actions 에서 자동 컴파일됨 (상대경로 사용)

[Setup]
; 고정 AppId — 업그레이드·제거 시 같은 프로그램으로 인식하게 함 (바꾸지 말 것)
AppId={{9F3B2C10-4E5D-4A21-9C7E-1A2B3C4D5E6F}}
AppName=폰 검수
AppVersion=1.0
AppPublisher=중고폰 검수
DefaultDirName={autopf}\PhoneInspector
DefaultGroupName=폰 검수
; 프로그램 그룹 선택 페이지는 생략(자동)
DisableProgramGroupPage=yes
OutputBaseFilename=PhoneInspector_Setup
OutputDir=Output
Compression=lzma2
SolidCompression=yes
; 64비트 전용 설치
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; 최신 스타일 마법사 UI
WizardStyle=modern
; 한글 설치 화면
ShowLanguageDialog=no
; 제어판 프로그램 제거에 표시될 정보
UninstallDisplayName=폰 검수
UninstallDisplayIcon={app}\phone_inspector.exe

[Languages]
Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"

[Tasks]
; 바탕화면 바로가기 만들기 (설치 중 체크박스로 선택)
Name: "desktopicon"; Description: "바탕화면에 바로가기 만들기"; GroupDescription: "추가 아이콘:"

[Files]
; 빌드 결과(Release) 폴더 전체를 포함 — platform-tools(adb) 포함됨
Source: "..\build\windows\x64\runner\Release\*"; DestDir: "{app}"; \
  Flags: recursesubdirs createallsubdirs

[Icons]
; 시작 메뉴 바로가기 (+ 제거 바로가기)
Name: "{group}\폰 검수"; Filename: "{app}\phone_inspector.exe"
Name: "{group}\폰 검수 제거"; Filename: "{uninstallexe}"
; 바탕화면 바로가기 (위 desktopicon 태스크 선택 시에만)
Name: "{autodesktop}\폰 검수"; Filename: "{app}\phone_inspector.exe"; \
  Tasks: desktopicon

[Run]
Filename: "{app}\phone_inspector.exe"; Description: "폰 검수 실행"; \
  Flags: nowait postinstall skipifsilent
