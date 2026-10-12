# Navi Mirror — 1차 구현 설계

## 확인된 기반과 단계

사용자가 0.2의 자동 실행과 R 후방 / D 전방 카메라 표시를 확인했다. GPS 수신은 보류했다. 이번 단계는 Z Flip7 화면 전송과 iQ 3 수신의 검증용 APK이다. 완성형 홈·주행 계기판은 실물 미러링 검증 후 진행한다.

## 앱과 플랫폼

- 수신: local.navi.diagnostic 0.3, min API 21 / target 28, 기존 서명·부팅 수신기 유지.
- 송신: local.navi.sender 0.1, min API 29 / target 35, 사용자가 승인한 MediaProjection만 사용.
- 네이티브 Java / Android framework. 외부 런타임·Play Services 의존성 없음.
- PHONE: MediaProjection → MediaCodec AVC surface encoder → bounded queue → encrypted TCP.
- NAVI: TCP → authenticated frame → MediaCodec AVC decoder → TextureView.
- 동일 공유기 또는 Z Flip7 핫스팟에 iQ 3를 연결한다. 별도 인터넷 서버를 사용하지 않는다.

## 영상·수명주기

800×480 고정 캔버스, 목표 15fps / 2Mbps / Baseline AVC, 1초 간격 keyframe. Android 12L 이상은 고정 Surface에 원본 화면 비율을 유지해 맞춘다. 회전 시 재승인이나 VirtualDisplay 재생성 없이 여백으로 처리한다. 송신 원본 크기는 진단 상태에 표시한다.

휴대폰 화면 공유는 foreground service에서 실행한다. 토큰당 VirtualDisplay 하나만 생성한다. 공유 중지 콜백뿐 아니라 SCREEN_OFF도 받아 세션을 종료한다. 화면 꺼짐 뒤에는 새 동의를 받아야 한다. 네트워크 단절은 승인된 세션 안에서 재연결한다. 캡처가 끝난 뒤 자동으로 토큰을 재사용하지 않는다.

수신 Activity는 카메라로 화면을 넘길 때 렌더링을 중단한다. 디코더 호출을 직렬화하고 별도 해제 작업을 보내므로 네트워크 read 제한 시간까지 기다리지 않고 디코더를 해제한다. 복귀 시 디코더를 새로 준비해 keyframe부터 재개한다. 강제 startActivity, 감시 루프, 오버레이 권한을 사용하지 않는다. 프레임이 2초 이상 없으면 다음 UI 갱신(1초 주기)에 지도를 가린다.

대기열 초과 시 이전 GOP를 버리고 keyframe까지 기다려 종속 프레임만 재생되는 것을 방지한다. 송신 write가 4초 넘게 막히면 소켓을 닫는다. 수신 read와 인증에는 5초 제한이 있다.

## 로컬 프로토콜 v1

수신자가 TCP 52741에서 대기한다. 수신 화면 진입 후 연결 대기를 누르면 SecureRandom 64-bit(16 hex) 코드가 생성된다. 코드는 기기에 저장하거나 보고서에 남기지 않는다.

서버/클라이언트 각각 32-byte random nonce와 HMAC-SHA256으로 코드 일치를 상호 확인한다. 세션별 파생 키를 사용한 AES-128-GCM으로 송신→수신 패킷을 암호화한다. 패킷마다 증가하는 순서 번호로 nonce를 생성하며 변조·순서 재사용은 인증 실패가 된다. 길이를 검사한 뒤에만 메모리를 할당한다(최대 plaintext 1MiB).

메시지: FORMAT(크기/SPS/PPS), FRAME(PTS/flags/AVC access unit), HEARTBEAT, HIDDEN(선택한 앱이 가려짐). 화면·음성·좌표를 저장하지 않는다. 터치 역전송과 오디오 캡처는 구현 범위에 없다.

## 디자인 기준과 후속 화면

검증 UI도 차콜 #111820, 청록 #69D7C6, 밝은 글자 #ECF1F5를 사용한다. 다음 단계에는 홈, 지도 연결, 즐겨찾는 앱, 설정·진단의 네 화면을 완성한다. 현재 단계에서 가짜 속도·기어 정보나 작동하지 않는 기능 타일을 추가하지 않는다.

참고: Google Design for Driving의 가독성·간격 / Polestar의 단순한 타일과 홈 복귀 구조.
- https://developers.google.com/cars/design/automotive-os/design-system/overview
- https://www.polestar.com/us/manual/polestar-2/2023/article/57895cfcfa3e7b68c0a8015125547be3
- https://developer.android.com/media/grow/media-projection

## 실물 판정

미러링 10분, 디코더 출력 10fps 이상, 외부 촬영으로 측정한 전체 지연 500ms 이하, R/D 카메라 전환 및 앱 복귀 확인. 에뮬레이터 결과는 iQ 3 하드웨어·Z Flip7 실제 호환성의 증거로 대체하지 않는다.
