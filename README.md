# LSPosed Center Dock (DeX Taskbar Center)

Module LSPosed/Xposed giúp căn giữa cụm taskbar trên Samsung DeX (nút Start + khu vực Dock/Pinned/Recent) bằng cách hook vào `SystemUI/DeXSystemUI`.

## Tính năng

- Hook vào `com.sec.android.dexsystemui` (và `com.android.systemui` khi cần).
- Căn giữa cụm Start + Dock theo chiều ngang.
- Giữ khoảng cách giữa:
  - Start ↔ Dock
  - Pinned apps ↔ Recent apps
- Tự cập nhật vị trí khi layout thay đổi.
- Ghi log debug với tag `DEX_DEBUG`.

## Thông tin kỹ thuật

- Package module: `com.example.dextaskbarcenter`
- Entry class Xposed: `com.example.dexcenter.DexTaskbarHook`
- Min Xposed API: `82`
- Android:
  - `minSdk 26`
  - `targetSdk 34`
  - `compileSdk 34`

## Yêu cầu

- Thiết bị Samsung có DeX.
- Đã cài LSPosed (hoặc môi trường tương thích Xposed API 82+).
- Kích hoạt module trong LSPosed cho tiến trình mục tiêu.

## Build APK

```bash
./gradlew assembleDebug
```

APK debug sẽ nằm tại:

`app/build/outputs/apk/debug/app-debug.apk`

## Cài đặt và sử dụng

1. Build APK và cài lên thiết bị.
2. Mở LSPosed, bật module **DeX Taskbar Center**.
3. Scope module cho:
   - `com.sec.android.dexsystemui`
   - (tuỳ ROM) `com.android.systemui`
4. Reboot hoặc restart SystemUI/DeX để áp dụng.

## Log debug

Theo dõi log để kiểm tra hook:

- `[DEX_DEBUG] Hooked TaskBarView onFinishInflate.`
- `[DEX_DEBUG] center: ...`

Bạn có thể dùng `logcat` để xem log runtime.

## Lưu ý

- Module phụ thuộc vào class/view nội bộ của Samsung DeX, có thể thay đổi theo phiên bản One UI/firmware.
- Nếu sau cập nhật hệ thống module không hoạt động, cần điều chỉnh logic hook trong `DexTaskbarHook`.
