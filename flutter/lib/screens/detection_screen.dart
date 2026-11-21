import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_vision/flutter_vision.dart';
import '../services/drone_service.dart';

class DetectionScreen extends StatefulWidget {
  const DetectionScreen({super.key});

  @override
  State<DetectionScreen> createState() => _DetectionScreenState();
}

class _DetectionScreenState extends State<DetectionScreen> {
  final FlutterVision _vision = FlutterVision();

  bool _modelLoaded = false;
  bool _detecting = false;
  String _status = 'Memuatkan model...';

  Uint8List? _frameBytes;
  List<Map<String, dynamic>> _detections = [];
  int _imgW = 1, _imgH = 1;

  @override
  void initState() {
    super.initState();
    _loadModel();
  }

  @override
  void dispose() {
    _vision.closeYoloModel();
    super.dispose();
  }

  Future<void> _loadModel() async {
    try {
      await _vision.loadYoloModel(
        labels: 'assets/yolo/labels.txt',
        modelPath: 'assets/yolo/Yolo-v8-Detection.tflite',
        modelVersion: 'yolov8',
        numThreads: 4,
        useGpu: false,
      );
      setState(() {
        _modelLoaded = true;
        _status = 'Model siap. Tekan Detect untuk mula.';
      });
    } catch (e) {
      setState(() => _status = 'Gagal muatkan model: $e');
    }
  }

  Future<void> _detect() async {
    if (!_modelLoaded || _detecting) return;
    setState(() {
      _detecting = true;
      _status = 'Capture frame...';
      _detections = [];
      _frameBytes = null;
    });

    try {
      final bytes = await DroneService.captureFrame();

      // Decode untuk dapat dimensi
      final decoded = await decodeImageFromList(bytes);
      final w = decoded.width;
      final h = decoded.height;

      setState(() => _status = 'Mengesan objek...');

      final results = await _vision.yoloOnImage(
        bytesList: bytes,
        imageHeight: h,
        imageWidth: w,
        iouThreshold: 0.4,
        confThreshold: 0.5,
        classThreshold: 0.5,
      );

      setState(() {
        _frameBytes = bytes;
        _detections = results;
        _imgW = w;
        _imgH = h;
        _status = '${results.length} objek dikesan';
        _detecting = false;
      });
    } catch (e) {
      setState(() {
        _status = 'Error: $e';
        _detecting = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        iconTheme: const IconThemeData(color: Colors.white),
        title: const Text('Object Detection',
            style: TextStyle(color: Colors.white, fontSize: 15)),
        actions: [
          if (_detecting)
            const Padding(
              padding: EdgeInsets.all(16),
              child: SizedBox(
                width: 18, height: 18,
                child: CircularProgressIndicator(
                    strokeWidth: 2, color: Colors.greenAccent),
              ),
            ),
        ],
      ),
      body: Column(
        children: [
          // ── Frame + bounding boxes ──────────────────────────────────────
          Expanded(
            child: _frameBytes == null
                ? Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.document_scanner_rounded,
                            size: 64, color: Colors.white24),
                        const SizedBox(height: 16),
                        Text(_status,
                            style: const TextStyle(
                                color: Colors.white54, fontSize: 13)),
                      ],
                    ),
                  )
                : _buildDetectionView(),
          ),

          // ── Status + butang ─────────────────────────────────────────────
          Container(
            color: Colors.black,
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            child: Column(
              children: [
                if (_frameBytes != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text(_status,
                        style: const TextStyle(
                            color: Colors.white54, fontSize: 11)),
                  ),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.greenAccent,
                      foregroundColor: Colors.black,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                    ),
                    onPressed: _modelLoaded && !_detecting ? _detect : null,
                    icon: const Icon(Icons.document_scanner_rounded),
                    label: Text(_detecting ? 'Mengesan...' : 'Detect Objek',
                        style: const TextStyle(
                            fontWeight: FontWeight.bold, fontSize: 15)),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDetectionView() {
    return LayoutBuilder(builder: (context, constraints) {
      final scale = constraints.maxWidth / _imgW;
      final displayH = _imgH * scale;

      return SingleChildScrollView(
        child: SizedBox(
          width: constraints.maxWidth,
          height: displayH,
          child: Stack(
            children: [
              // Gambar penuh
              Image.memory(_frameBytes!,
                  width: constraints.maxWidth,
                  height: displayH,
                  fit: BoxFit.fill),

              // Bounding boxes
              ..._detections.map((det) {
                final box = det['box'] as Map;
                final label = det['tag'] as String? ?? '';
                final conf = ((det['box']['confidence'] as num? ?? 0) * 100)
                    .toStringAsFixed(0);

                final x1 = (box['x1'] as num).toDouble() * scale;
                final y1 = (box['y1'] as num).toDouble() * scale;
                final x2 = (box['x2'] as num).toDouble() * scale;
                final y2 = (box['y2'] as num).toDouble() * scale;
                final w = x2 - x1;
                final h = y2 - y1;

                final color = _colorForLabel(label);

                return Positioned(
                  left: x1,
                  top: y1,
                  width: w,
                  height: h,
                  child: Stack(
                    clipBehavior: Clip.none,
                    children: [
                      Container(
                        decoration: BoxDecoration(
                          border: Border.all(color: color, width: 2),
                        ),
                      ),
                      Positioned(
                        top: -20,
                        left: 0,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 5, vertical: 2),
                          color: color,
                          child: Text('$label $conf%',
                              style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold)),
                        ),
                      ),
                    ],
                  ),
                );
              }),
            ],
          ),
        ),
      );
    });
  }

  Color _colorForLabel(String label) {
    final colors = [
      Colors.greenAccent, Colors.redAccent, Colors.blueAccent,
      Colors.orangeAccent, Colors.purpleAccent, Colors.cyanAccent,
      Colors.yellowAccent, Colors.pinkAccent,
    ];
    return colors[label.hashCode.abs() % colors.length];
  }
}
