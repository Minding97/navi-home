# Navi 홈 0.6 검증

## 산출물

- `staging/NaviDiagnostic-0.6.apk`, package `local.navi.diagnostic`, versionCode6/versionName0.6, min21/target28.
- APK SHA-256: `2c9c33e9f91460c142abab00207fc615cf4069f0708ad0f86fe896761f4c35b4`.
- 서명 인증서 SHA-256: `7029de6f57992c9e5aafe78ea5acceef7b57883d86b14b9fcf6852115148840b` (기존과 동일).
- `staging/Navi-offline-home-0.6.zip` SHA-256: `ad171cead50d35cf3b92031f1207bb71cb85e2cef96d29e4642b56357fa5341f`.
- ZIP의 APK와 최종 APK가 동일하며 CRC 검사 통과. 앱 내부 사용 안내와 배포 TXT가 동일함을 확인.

## PC에서 확인한 것

- Java/D8/aapt 빌드 및 최소 API21 기준 APK 서명 검사 통과. 기존 패키지·서명 유지.
- `tools/test_trip.py`: **24개 검사 통과**. GPS 미수신/속도 미제공/정지 흔들림/정확도 저하/10초 유효기간/5초 초과 간격/좌표 순간 이동/시각 역전/미래 및 오래된 좌표/프로세스 초기화 처리.
- `tools/emulator_offline_smoke.py`: 최종 APK, 별도 API36 800×480 에뮬레이터에서 **19개 검사 통과**. Wi-Fi·데이터를 끄고 홈 실행, 부팅 설정 유지, GPS 중지, 모의 속도36km/h, 모의 이동거리, 다른 앱으로 전환 중 GPS 유지, 강제 화면 복귀 없음, 위치 설정 꺼짐, 즐겨찾기·앱 실행·목록·오프라인 안내·USB 진단·제조사 앱 미설치 처리·세션 초기화·프로세스 재시작·GPS 서비스 종료.
- UI 자동화 도중 시스템 위치 안내창과 목록/스크롤 선택을 처리하도록 시험 도구를 수정했다. 해당 단계부터 같은 최종 APK로 검사를 이어갔다. 원시 로그는 `offline-emulator-log.txt`, 최종 통과 목록과 APK 해시는 `offline-emulator-checks.json`.
- API 정적 검사: 플랫폼 호출429개 확인. 신규 API23 권한 검사와 API26 알림 채널/생성자는 버전 분기로 보호한다. 상세 진단의 기존 API23 권한 결과 콜백은 API21에서 호출되지 않는다. 상속된 Activity의 `startForegroundService`, 권한 요청도 코드에서 SDK 조건을 확인했다. API21 실물 실행 검증을 대신하지 않는다.
- `offline-home-no-gps.png`, `offline-home-gps-simulated.png`, `offline-apps-800x480.png`, `offline-settings-800x480.png`의 배치와 가독성을 검토했다. GPS가 표시된 캡처는 **모의 입력**이다. 별도의 가짜 속도 표시나 데모 데이터를 제품 앱에 넣지 않았다.

추가 설정 검사 **8개 통과**: 밝기·음량 변경 및 재실행 후 유지, 위치 권한 안내에서 실제 거부 후 GPS 권한 필요 표시, 0.5에서 자동 실행을 명시적으로 끈 뒤 0.6 업데이트에도 꺼짐 유지, 관련 Java 충돌 없음. `offline-settings-checks.json`과 `offline-settings-log.txt` 참조. 전체 집계는 GPS 계산 24개 + 에뮬레이터 27개다.

## 사용자 실물 확인과 미확인

- **확인:** 초기 APK 설치·실행, 0.2 부팅 자동 실행, R 후방/D 전방 카메라 표시, 사용자가 보고한 GPS 수신.
- **미확인:** 0.6 실물 설치, 제조사 Android5 GPS 서비스 장시간 유지, 실제 속도·거리 정확도, 0.6 화면과 R/D 카메라 공존, 제조사 오디오 경로/밝기 반응, 동글 호환성, 미러링/Android Auto 실물 영상.
- **2026-09-17 SD 배포 완료:** 0.6 APK·사용 안내 복사와 해시 검증, 이전 홈 APK 0.1~0.4 삭제, 안전 추출 완료. 정리 전 NaviTest는 PC ZIP 백업에 보존했다. 휴대폰 송신 APK와 제조사 파일은 유지했다. 내비 본체 설치·실행은 아직 미확인이다. 상세: `car-test-deployment-0.6.json`.

## 구현의 한계

- GPS 정확도30m 이하, 속도1.5m/s 이상인 연속 좌표의 거리만 집계한다. 매우 느린 서행은 덜 집계될 수 있다. GPS 미수신 구간은 보간하지 않는다.
- 속도는 제공된 GPS 값만 사용한다. 위치만 있고 속도가 없으면 — 이다. 오래된 값도 — 이다.
- ‘세션 경과 시간’은 세션 시작 이후 시간이며 실제 이동 시간만 의미하지 않는다. 기기 벽시계 변경으로 집계 시간이 튀지 않도록 monotonic clock을 사용한다.
- 좌표·경로·집계는 디스크에 저장하지 않는다. 메모리에는 집계와 직전 좌표만 있다. 시스템이 프로세스를 종료하면 현재 집계를 잃는다.
- 홈 밝기는 이 Activity 창에만 적용된다. 미디어 음량은 Android STREAM_MUSIC이며 제조사 전용 오디오 경로는 별도다.
- foreground GPS 서비스는 알림을 표시하며 카메라 위에 창을 띄우지 않는다. 서비스는 START_NOT_STICKY이고 사용자가 세션 종료를 선택하면 수신·서비스·알림을 끝낸다.
- 기존 미러링·Wi-Fi·Android Auto 시험 코드와 설정을 유지했다. 새 홈의 실행 가능 앱 목록은 아틀란 패키지명을 제외하고 기본 즐겨찾기는 비워 둔다.
