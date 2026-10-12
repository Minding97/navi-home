# 저녁 시험 준비 — Navi 진단 0.5

## 전달물

- `staging/Navi-evening-test-0.5.zip`: SD카드에 추가할 파일 묶음.
- `NaviDiagnostic-0.5.apk`: 기존 패키지·서명을 유지한 업데이트. 오프라인 안내, Open Headunit 실행/연결/종료, 수동 시험 기록과 파일 내보내기를 추가했다.
- `OpenHeadunit-3.3.0.apk`: upstream 공식 정식 3.3.0 APK. 변경하거나 재서명하지 않았다.
- `NaviSender-0.1.apk`: 기존 미러링 송신 앱. 이번 작업에서 변경하지 않았다.
- `EVENING-TEST-KO.txt`: 준비, Wi-Fi, 기존 미러링, Android Auto, 카메라, 복구를 순서대로 안내한다. 동일 파일이 0.5 APK 안에도 포함된다.
- `EVENING-RESULT-TEMPLATE.txt`: 수동 기록 양식. 앱에서도 미확인·통과·실패·건너뜀을 기록할 수 있다.

## 검증의 의미

`evening-address-tests.txt`는 로컬 IPv4 입력 검증 18개를 기록한다. 외부 호스트명, URL, 포트가 붙은 주소, 잘못된 주소를 거부한다.

`evening-api-review.txt`와 `evening-api-inventory.json`은 추가 코드가 API 21 범위인지 정적으로 확인한 자료다. API 23로 표시되는 호출은 기존 권한 결과 콜백 한 곳이다.

`evening-emulator-checks.json`은 임시 API 36 / 800×480 에뮬레이터 검사 결과다. 이 파일의 목록에 있는 검사만 통과 범위다. 연결 시험에 사용하는 로컬 TCP 서버는 전화기나 Android Auto 서버를 구현하지 않는다. 따라서 TCP 연결 성공은 실제 Android Auto 투영 성공을 의미하지 않는다.

최초 UI 시험은 Open Headunit의 Android 36 위치 권한 창 때문에 실행 화면 확인에서 멈췄다. Java 충돌은 없었으며, 테스트 도구가 표준 권한 창을 처리하도록 수정했다. 다른 앱 위 표시 권한은 부여하지 않았다.

## Open Headunit 출처 및 호환성

- 공식 배포: https://github.com/andreknieriem/open-headunit/releases/tag/v.3.3.0
- GitHub Releases API의 asset digest와 내려받은 APK의 SHA-256이 일치한다.
- API 21 대상으로 v1/v2 APK 서명 검증이 성공했다.
- 최소 API 16. ARM 32비트와 ARM 64비트 코드가 모두 포함되어 있다.
- 세부 출처·해시·서명 정보: `openhu-provenance.json`, `openhu-signature.txt`, `openhu-badging.txt`.
- 출처 안내와 upstream AGPL 라이선스를 시험 묶음에 포함했다.

3.3.0 소스에서 공개 MainActivity가 `headunit://connect?ip=...`, `headunit://exit`를 처리하는 것을 확인했다. 연결 요청은 해당 MainActivity로 명시적으로 전달한다. 투명 AutomationActivity는 즉시 종료되면서 비동기 작업이 취소될 여지가 있어 사용하지 않는다. 우리 앱은 상대 앱의 비공개 저장소나 설정을 수정하지 않는다.

무선 시험은 휴대폰 핫스팟과 Android Auto의 Headunit Server를 사용한다. 새 장비 구매나 무선 자동 시작을 전제로 하지 않는다. USB 케이블이 있으면 별도로 유선 연결을 비교할 수 있다.

## 실물에서 남은 것

iQ 3의 실제 Android 5 런타임, 동글 인식, Z Flip7의 실제 Android Auto 버전·서버 동작, 네이버지도 화면·터치, 음성 출력, 10분 유지, 카메라 우선 표시와 복귀는 사용자가 저녁에 확인해야 한다. 기존 사용자 확인은 이전 앱의 자동 실행과 R/D 카메라 표시이며 새 Open Headunit과의 공존을 증명하지 않는다.

SD카드가 현재 다시 연결되어 있지 않아 0.5 카드 복사는 수행하지 않았다. `car-test-deployment-0.5.json`에 준비 상태를 기록했다. 연결되면 `python3 tools/prepare_car_test.py --version 0.5`로 식별·백업 확인 후 새 파일만 추가한다.

## 최종 확인

최종 APK 기준 에뮬레이터 검사 17개 통과. 주소 입력 시험 18개 통과. 실제 Android Auto 투영은 미시험이다.

수신 APK SHA-256: `3a9683bbea095b3afd11d10be6c8e8678e1cf8b7178864c6293a327c74fa0bc2`
