; 폰 검수 프로그램 설치 마법사 스크립트
; GitHub Actions 에서 자동 컴파일됨 (상대경로 사용)

[Setup]
AppName=폰 검수
AppVersion=1.0
AppPublisher=중고폰 검수
DefaultDirName={autopf}\PhoneInspector
DefaultGroupName=폰 검수
OutputBaseFilename=PhoneInspector_Setup
OutputDir=Output
Compression=lzma2
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64
; 한글 설치 화면
ShowLanguageDialog=no

[Languages]
Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"

[Files]
; 빌드 결과(Release) 폴더 전체를 포함 — platform-tools(adb) 포함됨
Source: "..\build\windows\x64\runner\Release\*"; DestDir: "{app}"; \
  Flags: recursesubdirs createallsubdirs

[Icons]
Name: "{group}\폰 검수"; Filename: "{app}\phone_inspector.exe"
Name: "{commondesktop}\폰 검수"; Filename: "{app}\phone_inspector.exe"

[Run]
Filename: "{app}\phone_inspector.exe"; Description: "폰 검수 실행"; \
  Flags: nowait postinstall skipifsilent
