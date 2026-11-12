import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import '../services/drone_service.dart';
import '../main.dart' show apiBaseUrl;

class DroneScreen extends StatefulWidget {
  const DroneScreen({super.key});

  @override
  State<DroneScreen> createState() => _DroneScreenState();
}

class _DroneScreenState extends State<DroneScreen> {
  String _droneStatus = 'disconnected';
  String _log = '';
  bool _loading = false;
  bool _sdkRegistered = false;
  bool _isRecording = false;
  bool _isScanning = false;
  Timer? _statusTimer;

  // Halal scan results
  List<dynamic> _scanResults = [];
  String _scanStatus = '';   // 'halal' | 'not_found' | ''
  String _detectedText = '';

  bool get _isConnected => _droneStatus.startsWith('connected:');

  @override
  void initState() {
    super.initState();
    _initState();
  }

  Future<void> _initState() async {
    final alreadyRegistered = await DroneService.isSDKRegistered();
    if (alreadyRegistered) setState(() => _sdkRegistered = true);
    await _checkStatus();
    // Auto-refresh status setiap 5 saat sampai connected
    _statusTimer = Timer.periodic(const Duration(seconds: 5), (_) {
      if (!_isConnected && mounted) _checkStatus();
    });
  }

  @override
  void dispose() {
    _statusTimer?.cancel();
    super.dispose();
  }

  Future<void> _registerSDK() async {
    setState(() { _loading = true; _log = 'Mendaftar SDK...'; });
    try {
      final result = await DroneService.registerSDK();
      setState(() { _sdkRegistered = true; _log = 'SDK berjaya ($result)'; });
      await _checkStatus();
    } catch (e) {
      setState(() => _log = 'Pendaftaran gagal: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _checkStatus() async {
    setState(() => _loading = true);
    try {
      final status = await DroneService.getDroneStatus();
      setState(() {
        _droneStatus = status;
        if (_isConnected) {
          _log = '${DroneService.getModelName(status)} bersambung';
        } else {
          final reason = status.contains(':')
              ? status.split(':').skip(1).join(':').trim()
              : '';
          _log = reason.isNotEmpty ? reason : 'Drone tidak bersambung';
        }
      });
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _capturePhoto() async {
    setState(() { _loading = true; _log = 'Mengambil gambar...'; });
    try {
      await DroneService.capturePhoto();
      setState(() => _log = 'Gambar disimpan dalam SD card drone.');
    } catch (e) {
      // DJI Mini 2 tak support photo via SDK semasa live stream
      setState(() => _log = 'Guna butang RC untuk ambil gambar (SDK limitation)');
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _toggleRecording() async {
    setState(() => _loading = true);
    try {
      if (_isRecording) {
        await DroneService.stopRecording();
        setState(() { _isRecording = false; _log = 'Video disimpan dalam SD card drone.'; });
      } else {
        await DroneService.startRecording();
        setState(() { _isRecording = true; _log = 'Sedang merekod...'; });
      }
    } catch (e) {
      setState(() => _log = 'Ralat rekod: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  // ── Scan halal: capture frame → POST ke /scan-image → tunjuk result ────────
  Future<void> _scanHalal() async {
    setState(() {
      _isScanning = true;
      _scanResults = [];
      _scanStatus = '';
      _detectedText = '';
      _log = 'Mengimbas logo/nama halal...';
    });

    try {
      // 1. Capture frame dari drone camera
      setState(() => _log = '[1/3] Capture frame...');
      final Uint8List frameBytes = await DroneService.captureFrame();
      final kb = (frameBytes.length / 1024).toStringAsFixed(1);
      setState(() => _log = '[2/3] Frame ${kb}KB → server...');

      final uri = Uri.parse('$apiBaseUrl/scan-premises');
      final request = http.MultipartRequest('POST', uri)
        ..files.add(http.MultipartFile.fromBytes(
          'file',
          frameBytes,
          filename: 'drone_frame.jpg',
        ));

      final response = await request.send().timeout(const Duration(seconds: 60));
      final body = await response.stream.bytesToString();
      final data = jsonDecode(body) as Map<String, dynamic>;

      // /scan-image guna 'halal_records', /scan-premises guna 'matches'
      final matches = (data['matches'] ?? data['halal_records']) as List? ?? [];
      final visionResult = data['vision_result'] as Map<String, dynamic>? ?? {};
      final visionTexts = data['vision_texts'] as List? ??
          [visionResult['brand'], visionResult['product']]
              .whereType<String>()
              .where((s) => s.isNotEmpty)
              .toList();
      final ocrTexts = data['ocr_texts'] as List? ?? [];

      final allTexts = [...visionTexts, ...ocrTexts]
          .map((t) => t.toString())
          .toSet()
          .take(5)
          .toList();
      final allDetected = allTexts.join(' | ');

      setState(() {
        _scanResults = matches;
        _detectedText = allDetected;
        _scanStatus = matches.isNotEmpty ? 'halal' : 'not_found';
        _log = matches.isNotEmpty
            ? '[3/3] ${matches.length} premis dijumpai'
            : '[3/3] Tiada rekod — dikesan: "$allDetected"';
      });
    } catch (e) {
      setState(() {
        _scanStatus = 'error';
        _log = 'ERROR: $e';
      });
    } finally {
      setState(() => _isScanning = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        iconTheme: const IconThemeData(color: Colors.white),
        title: Row(
          children: [
            Container(
              width: 8, height: 8,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: _isConnected ? Colors.greenAccent : Colors.red,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              _isConnected ? DroneService.getModelName(_droneStatus) : 'Drone Scanner',
              style: const TextStyle(color: Colors.white, fontSize: 15),
            ),
            if (_isRecording) ...[
              const SizedBox(width: 10),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                decoration: BoxDecoration(
                  color: Colors.red,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: const Text('REC',
                    style: TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold)),
              ),
            ],
          ],
        ),
        actions: [
          if (_loading || _isScanning)
            const Padding(
              padding: EdgeInsets.all(16),
              child: SizedBox(width: 18, height: 18,
                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white)),
            ),
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white70),
            onPressed: _checkStatus,
          ),
        ],
      ),
      body: Column(
        children: [
          // ── Live camera feed 16:9 ───────────────────────────────────────
          AspectRatio(
            aspectRatio: 16 / 9,
            child: Stack(
              children: [
                // AndroidView always in tree so DroneCameraView always exists
                const AndroidView(
                  viewType: 'smart.app/drone_camera',
                  layoutDirection: TextDirection.ltr,
                  creationParamsCodec: StandardMessageCodec(),
                ),

                // "Not connected" overlay — shown on top when no drone
                if (!_isConnected)
                  _buildNoConnectionOverlay(),

                // Scanning overlay
                if (_isScanning)
                  Container(
                    color: Colors.black54,
                    child: Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: const [
                          CircularProgressIndicator(color: Colors.greenAccent),
                          SizedBox(height: 12),
                          Text('Mengimbas...', style: TextStyle(color: Colors.white)),
                        ],
                      ),
                    ),
                  ),

                // Scan result badge (top-right corner of feed)
                if (_scanStatus.isNotEmpty && !_isScanning)
                  Positioned(
                    top: 8, right: 8,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                      decoration: BoxDecoration(
                        color: _scanStatus == 'halal'
                            ? Colors.green.shade700
                            : Colors.red.shade700,
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        _scanStatus == 'halal' ? '✓ HALAL' : '✗ TIADA REKOD',
                        style: const TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
              ],
            ),
          ),

          // ── Controls + Results (scrollable) ──────────────────────────────
          Expanded(
            child: SingleChildScrollView(
              child: Column(
                children: [
                  // Buttons row
                  Container(
                    color: Colors.black,
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                    child: Column(
                      children: [
                        if (_log.isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 8),
                            child: Text(_log,
                                style: const TextStyle(color: Colors.white54, fontSize: 11),
                                textAlign: TextAlign.center),
                          ),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                          children: [
                            _buildControlBtn(
                              icon: Icons.key_rounded,
                              label: _sdkRegistered ? 'SDK ✓' : 'Daftar',
                              color: _sdkRegistered ? Colors.white30 : Colors.blueAccent,
                              onTap: _sdkRegistered ? null : _registerSDK,
                            ),
                            _buildControlBtn(
                              icon: Icons.document_scanner_rounded,
                              label: 'Scan Halal',
                              color: Colors.greenAccent,
                              onTap: _sdkRegistered && !_isScanning ? _scanHalal : null,
                              large: true,
                            ),
                            _buildControlBtn(
                              icon: Icons.camera_rounded,
                              label: 'Gambar',
                              color: Colors.white,
                              onTap: _isConnected && !_loading ? _capturePhoto : null,
                            ),
                            _buildControlBtn(
                              icon: _isRecording ? Icons.stop_rounded : Icons.videocam_rounded,
                              label: _isRecording ? 'Stop' : 'Video',
                              color: _isRecording ? Colors.red : Colors.white,
                              onTap: _isConnected && !_loading ? _toggleRecording : null,
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                      ],
                    ),
                  ),

                  // Results list — bawah buttons
                  if (_scanResults.isNotEmpty || _scanStatus == 'not_found')
                    _buildResultsPanel(),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildResultsPanel() {
    return Container(
      width: double.infinity,
      color: Colors.black,
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Row(
            children: [
              Icon(
                _scanResults.isEmpty ? Icons.cancel_rounded : Icons.verified_rounded,
                color: _scanResults.isEmpty ? Colors.red : Colors.greenAccent,
                size: 16,
              ),
              const SizedBox(width: 6),
              Text(
                _scanResults.isEmpty
                    ? 'Tiada rekod halal dalam pangkalan data JAKIM'
                    : '${_scanResults.length} rekod halal dijumpai',
                style: TextStyle(
                  color: _scanResults.isEmpty ? Colors.red : Colors.greenAccent,
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
          if (_detectedText.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text('Dikesan: $_detectedText',
                style: const TextStyle(color: Colors.white38, fontSize: 10)),
          ],
          if (_scanResults.isNotEmpty) ...[
            const SizedBox(height: 10),
            ..._scanResults.map((r) => _buildResultCard(r)),
          ],
        ],
      ),
    );
  }

  Widget _buildResultCard(dynamic r) {
    final company = r['company'] ?? r['brand'] ?? '-';
    final address = r['address'] ?? '';
    final expiry  = r['expiry'] ?? '';
    final category = r['category'] ?? '';

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF0D1F14),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.green.shade900),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.store_rounded, color: Colors.greenAccent, size: 16),
              const SizedBox(width: 8),
              Expanded(
                child: Text(company,
                    style: const TextStyle(
                        color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600)),
              ),
              if (expiry.isNotEmpty)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.green.shade900,
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(expiry.toString(),
                      style: const TextStyle(color: Colors.greenAccent, fontSize: 9)),
                ),
            ],
          ),
          if (category.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(category.toString(),
                style: const TextStyle(color: Colors.white54, fontSize: 10)),
          ],
          if (address.isNotEmpty) ...[
            const SizedBox(height: 4),
            Row(
              children: [
                const Icon(Icons.location_on_rounded, color: Colors.white30, size: 11),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(address.toString(),
                      style: const TextStyle(color: Colors.white38, fontSize: 10),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildNoConnectionOverlay() {
    return Container(
      color: Colors.black87,
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.videocam_off_rounded, size: 48, color: Colors.white24),
            const SizedBox(height: 12),
            const Text('Sambungkan RC ke phone via USB-C',
                style: TextStyle(color: Colors.white38, fontSize: 12)),
            const SizedBox(height: 16),
            if (!_sdkRegistered)
              OutlinedButton.icon(
                icon: const Icon(Icons.key_rounded, color: Colors.blueAccent, size: 16),
                label: const Text('Daftar SDK',
                    style: TextStyle(color: Colors.blueAccent, fontSize: 13)),
                style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: Colors.blueAccent)),
                onPressed: _loading ? null : _registerSDK,
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildControlBtn({
    required IconData icon,
    required String label,
    required Color color,
    required VoidCallback? onTap,
    bool large = false,
  }) {
    final size = large ? 66.0 : 48.0;
    return GestureDetector(
      onTap: onTap,
      child: Opacity(
        opacity: onTap == null ? 0.3 : 1.0,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: size, height: size,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: color, width: large ? 3 : 2),
                color: large ? color.withValues(alpha: 0.15) : Colors.transparent,
              ),
              child: Icon(icon, color: color, size: large ? 30 : 22),
            ),
            const SizedBox(height: 4),
            Text(label,
                style: TextStyle(
                    color: color,
                    fontSize: 10,
                    fontWeight: large ? FontWeight.bold : FontWeight.normal)),
          ],
        ),
      ),
    );
  }
}
