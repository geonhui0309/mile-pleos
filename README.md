# MILE 마일

차량 이동 중 창밖 풍경의 이야기를 음성으로 듣고, 동승자가 고른 한마디를 여정에 남기는 Pleos Connect 차량용 앱입니다.

## 설치 및 실행

Pleos Connect 2.1.0 에뮬레이터를 실행하고 `adb devices`에서 기기 ID를 확인합니다.

```sh
adb -s emulator-5556 install apk/MILE_MILE_release.apk
adb -s emulator-5556 shell am start --user current -n io.gildongmu.voiceprobe/.MainActivity
```

예시의 `emulator-5556`은 실제 기기 ID로 바꿉니다. 차량 앱에서 **주변 이야기 시작 → 이야기 듣기**로 첨성대 해설을 재생합니다. **설정 → 울산바위 안내 재생 시험**에서 예고와 관찰 안내 시점을 확인할 수 있습니다. 장소 발견은 거리 재생 시연으로 동작합니다.

## 시험 환경

Pleos Connect 2.1.0, Android 14 기반, 차량 화면 2560×1440. 음성 입출력은 Pleos SDK 3.3.1 온디바이스 모드를 사용합니다. 기본 해설과 로컬 여정은 서버 연결 없이 실행됩니다. 자유 질문 AI 답변, 그림 자동 생성, 계정 동기화는 개발 서버를 연결한 별도 개발 환경에서 시험한 기능입니다. 해설 중 음성 호출은 기본값이 꺼진 시험 옵션입니다.

## 소스 및 APK

차량 앱 소스는 `vehicle/`에 있습니다. JDK 17 및 Android SDK 35가 필요합니다. `cd vehicle` 후 `./gradlew :app:assembleDebug`로 빌드합니다. 릴리스를 다시 빌드하려면 별도 서명이 필요합니다.

서명된 제출 APK는 [apk/MILE_MILE_release.apk](apk/MILE_MILE_release.apk)에 있습니다.
