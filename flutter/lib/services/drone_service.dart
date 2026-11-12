import 'package:flutter/services.dart';

/// Service untuk komunikasi dengan DJI Mobile SDK v4 melalui Method Channel.
///
/// Architecture:
///   DroneService (Dart)
///       ↕  MethodChannel("smart.app/dji")
///   MainActivity.kt (Android native)
///       ↕  DJI Mobile SDK v4
///   DJI Mini 2 (via RC + OcuSync 2.0)
class DroneService {
  static const _channel = MethodChannel('smart.app/dji');

  /// Semak sama ada DJI SDK sudah didaftar dalam sesi native ini.
  static Future<bool> isSDKRegistered() async {
    try {
      final result = await _channel.invokeMethod<bool>('isSDKRegistered');
      return result ?? false;
    } on PlatformException {
      return false;
    }
  }

  /// Daftar DJI App Key dengan server DJI dan mula scan untuk drone.
  /// Perlu internet. Panggil sekali sahaja semasa app start atau first use.
  ///
  /// Returns "registered" jika berjaya.
  /// Throws [Exception] jika App Key salah atau tiada internet.
  static Future<String> registerSDK() async {
    try {
      final result = await _channel.invokeMethod<String>('registerSDK');
      return result ?? 'unknown';
    } on PlatformException catch (e) {
      throw Exception('DJI SDK registration gagal: ${e.message}');
    }
  }

  /// Semak status sambungan drone semasa.
  ///
  /// Returns:
  ///   "connected:DJI Mini 2" — drone bersambung
  ///   "disconnected"          — tiada drone
  static Future<String> getDroneStatus() async {
    try {
      final result = await _channel.invokeMethod<String>('getDroneStatus');
      return result ?? 'disconnected';
    } on PlatformException catch (e) {
      return 'error: ${e.message}';
    }
  }

  /// Trigger kamera drone untuk ambil satu gambar.
  /// Gambar disimpan dalam SD card drone (bukan phone storage).
  ///
  /// Returns "photo_captured" jika berjaya.
  /// Throws [Exception] jika drone tidak bersambung atau kamera busy.
  static Future<String> capturePhoto() async {
    try {
      final result = await _channel.invokeMethod<String>('capturePhoto');
      return result ?? 'unknown';
    } on PlatformException catch (e) {
      throw Exception('Gagal ambil gambar: ${e.message}');
    }
  }

  /// Capture frame semasa dari live drone camera feed sebagai JPEG bytes.
  static Future<Uint8List> captureFrame() async {
    try {
      final result = await _channel.invokeMethod<Uint8List>('captureFrame');
      if (result == null || result.isEmpty) throw Exception('Frame kosong');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Gagal capture frame: ${e.message}');
    }
  }

  /// Mula rekod video. Returns "recording_started" jika berjaya.
  static Future<String> startRecording() async {
    try {
      final result = await _channel.invokeMethod<String>('startRecording');
      return result ?? 'unknown';
    } on PlatformException catch (e) {
      throw Exception('Gagal mula rekod: ${e.message}');
    }
  }

  /// Berhenti rekod video. Returns "recording_stopped" jika berjaya.
  static Future<String> stopRecording() async {
    try {
      final result = await _channel.invokeMethod<String>('stopRecording');
      return result ?? 'unknown';
    } on PlatformException catch (e) {
      throw Exception('Gagal berhenti rekod: ${e.message}');
    }
  }

  // ── Helper methods ──────────────────────────────────────────────────────

  /// True jika status string menunjukkan drone bersambung
  static bool isConnected(String status) => status.startsWith('connected:');

  /// Extract nama model dari status string ("connected:DJI Mini 2" → "DJI Mini 2")
  static String getModelName(String status) {
    if (!isConnected(status)) return '';
    return status.replaceFirst('connected:', '');
  }
}
