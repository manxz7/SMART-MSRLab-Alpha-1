import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:http/http.dart' as http;

import '../main.dart';

// ─────────────────────────────────────────────────────────────────────────────
// LogoScanScreen — scan halal certification logo → identify certifying body
//
// Flow:
//   1. User ambil gambar / pilih dari gallery
//   2. Hantar ke /scan-logo → Qwen Vision kenal pasti abbreviation + country
//   3. Cross-check dengan senarai 94 badan pensijilan JAKIM
//   4. Tunjuk hasil: nama badan, negara, email, website
// ─────────────────────────────────────────────────────────────────────────────
class LogoScanScreen extends StatefulWidget {
  const LogoScanScreen({super.key});

  @override
  State<LogoScanScreen> createState() => _LogoScanScreenState();
}

class _LogoScanScreenState extends State<LogoScanScreen> {
  final _picker = ImagePicker();

  File? _selectedImage;
  bool _loading = false;
  String? _errorMsg;

  // Hasil dari API
  Map<String, dynamic>? _visionResult;
  List<String> _extractedTexts = [];
  List<dynamic> _matches = [];
  bool? _isRecognised;
  String _detectionMethod = '';

  // ── Pilih gambar dari kamera atau gallery ─────────────────────────────────
  Future<void> _pickImage(ImageSource source) async {
    final picked = await _picker.pickImage(
      source: source,
      imageQuality: 85,
      maxWidth: 1200,
    );
    if (picked == null) return;

    setState(() {
      _selectedImage = File(picked.path);
      _visionResult = null;
      _matches = [];
      _isRecognised = null;
      _errorMsg = null;
    });

    await _scanLogo();
  }

  // ── Hantar gambar ke /scan-logo endpoint ──────────────────────────────────
  Future<void> _scanLogo() async {
    if (_selectedImage == null) return;

    setState(() {
      _loading = true;
      _errorMsg = null;
    });

    try {
      final uri = Uri.parse('$apiBaseUrl/scan-logo');
      final req = http.MultipartRequest('POST', uri);

      req.files.add(await http.MultipartFile.fromPath(
        'file',
        _selectedImage!.path,
      ));

      final streamed = await req.send().timeout(const Duration(seconds: 45));
      final body = await streamed.stream.bytesToString();

      if (streamed.statusCode != 200) {
        setState(() => _errorMsg = 'Server error ${streamed.statusCode}: $body');
        return;
      }

      final data = json.decode(body) as Map<String, dynamic>;
      setState(() {
        _visionResult = data['vision_result'] as Map<String, dynamic>?;
        _extractedTexts = List<String>.from(data['extracted_texts'] as List? ?? []);
        _matches = (data['matches'] as List?) ?? [];
        _isRecognised = data['is_recognised_by_jakim'] as bool?;
        _detectionMethod = (data['detection_method'] as String?) ?? '';
      });
    } catch (e) {
      setState(() => _errorMsg = 'Gagal scan: $e');
    } finally {
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF0F4F0),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1B7A4B),
        iconTheme: const IconThemeData(color: Colors.white),
        title: const Text(
          'Scan Logo Halal',
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // ── Header info card ───────────────────────────────────────────
            _buildInfoCard(),
            const SizedBox(height: 20),

            // ── Image preview + pick buttons ───────────────────────────────
            _buildImageSection(),
            const SizedBox(height: 20),

            // ── Loading indicator ──────────────────────────────────────────
            if (_loading) ...[
              const Center(
                child: Column(
                  children: [
                    CircularProgressIndicator(color: Color(0xFF1B7A4B)),
                    SizedBox(height: 12),
                    Text(
                      'Qwen Vision sedang menganalisis logo...',
                      style: TextStyle(color: Color(0xFF4A7A5A), fontSize: 13),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
            ],

            // ── Error ──────────────────────────────────────────────────────
            if (_errorMsg != null) ...[
              _buildErrorCard(_errorMsg!),
              const SizedBox(height: 20),
            ],

            // ── Result ────────────────────────────────────────────────────
            if (!_loading && _isRecognised != null) ...[
              _buildStatusBanner(),
              const SizedBox(height: 16),
              if (_visionResult != null) _buildVisionCard(),
              const SizedBox(height: 16),
              if (_matches.isNotEmpty) ..._matches.map(_buildMatchCard),
              if (_matches.isEmpty) _buildNoMatchCard(),
            ],
          ],
        ),
      ),
    );
  }

  // ── Info card atas ────────────────────────────────────────────────────────
  Widget _buildInfoCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF1B7A4B).withValues(alpha: 0.07),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFF1B7A4B).withValues(alpha: 0.2)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFF1B7A4B).withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.verified_rounded, color: Color(0xFF1B7A4B), size: 26),
          ),
          const SizedBox(width: 14),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Scan Logo Halal',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                    color: Color(0xFF1B4332),
                  ),
                ),
                SizedBox(height: 4),
                Text(
                  'Ambil gambar logo halal pada produk untuk semak sama ada badan pensijilan diiktiraf JAKIM.',
                  style: TextStyle(fontSize: 12, color: Color(0xFF4A7A5A), height: 1.4),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ── Image preview + pick buttons ──────────────────────────────────────────
  Widget _buildImageSection() {
    return Column(
      children: [
        // Preview box
        GestureDetector(
          onTap: () => _pickImage(ImageSource.camera),
          child: Container(
            height: 220,
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: _selectedImage != null
                    ? const Color(0xFF1B7A4B).withValues(alpha: 0.4)
                    : Colors.grey.shade300,
                width: 2,
              ),
            ),
            child: _selectedImage != null
                ? ClipRRect(
                    borderRadius: BorderRadius.circular(14),
                    child: Image.file(_selectedImage!, fit: BoxFit.contain),
                  )
                : Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.add_a_photo_rounded,
                          size: 48, color: Colors.grey.shade400),
                      const SizedBox(height: 10),
                      Text(
                        'Ketuk untuk ambil gambar',
                        style: TextStyle(color: Colors.grey.shade500, fontSize: 13),
                      ),
                    ],
                  ),
          ),
        ),
        const SizedBox(height: 14),
        // Camera + Gallery buttons
        Row(
          children: [
            Expanded(
              child: _buildPickButton(
                icon: Icons.camera_alt_rounded,
                label: 'Kamera',
                source: ImageSource.camera,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _buildPickButton(
                icon: Icons.photo_library_rounded,
                label: 'Gallery',
                source: ImageSource.gallery,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildPickButton({
    required IconData icon,
    required String label,
    required ImageSource source,
  }) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      elevation: 1,
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: _loading ? null : () => _pickImage(source),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 18, color: const Color(0xFF1B7A4B)),
              const SizedBox(width: 8),
              Text(
                label,
                style: const TextStyle(
                  fontWeight: FontWeight.w600,
                  fontSize: 13,
                  color: Color(0xFF1B4332),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ── Status banner: DIIKTIRAF / TIDAK DIIKTIRAF ────────────────────────────
  Widget _buildStatusBanner() {
    final isOk = _isRecognised == true;
    final color = isOk ? const Color(0xFF1B7A4B) : const Color(0xFFC62828);
    final bgColor = isOk
        ? const Color(0xFF1B7A4B).withValues(alpha: 0.08)
        : const Color(0xFFC62828).withValues(alpha: 0.07);
    final icon = isOk ? Icons.check_circle_rounded : Icons.cancel_rounded;
    final label = isOk
        ? 'Diiktiraf oleh JAKIM'
        : 'Tidak Dijumpai dalam Senarai JAKIM';

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 28),
          const SizedBox(width: 14),
          Expanded(
            child: Text(
              label,
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 15,
                color: color,
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ── Vision result card (apa yang Qwen baca) ───────────────────────────────
  Widget _buildVisionCard() {
    final isOcr = _detectionMethod == 'ocr';
    final methodLabel = isOcr ? 'OCR (PaddleOCR)' : 'Qwen Vision';
    final methodIcon = isOcr ? Icons.text_fields_rounded : Icons.psychology_rounded;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _rowLabel('Teks Dikesan ($methodLabel)', methodIcon, const Color(0xFF5C35A0)),
          const Divider(height: 20),
          if (_extractedTexts.isNotEmpty)
            _infoRow('Teks dalam logo', _extractedTexts.join(', '))
          else
            Text(
              'Tiada teks dikesan',
              style: TextStyle(fontSize: 12, color: Colors.grey.shade500),
            ),
        ],
      ),
    );
  }

  // ── Match card (details badan pensijilan) ─────────────────────────────────
  Widget _buildMatchCard(dynamic match) {
    final m = match as Map<String, dynamic>;
    final org = m['organization_name'] ?? '-';
    final country = m['country'] ?? '-';
    final abbr = m['abbreviation'] ?? '';
    final email = m['email'] ?? '';
    final website = m['website'] ?? '';
    final phone = m['phone'] ?? '';

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFF1B7A4B).withValues(alpha: 0.25)),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF1B7A4B).withValues(alpha: 0.05),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFF1B7A4B),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  abbr.isNotEmpty ? abbr : 'CB',
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 12,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Text(
                country,
                style: const TextStyle(
                  fontWeight: FontWeight.w600,
                  fontSize: 13,
                  color: Color(0xFF4A7A5A),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            org,
            style: const TextStyle(
              fontWeight: FontWeight.bold,
              fontSize: 13,
              color: Color(0xFF1B2A1F),
              height: 1.4,
            ),
          ),
          if (email.isNotEmpty) ...[
            const SizedBox(height: 8),
            _infoRow('Email', email, icon: Icons.email_outlined),
          ],
          if (website.isNotEmpty) ...[
            const SizedBox(height: 6),
            _infoRow('Website', website, icon: Icons.language_rounded),
          ],
          if (phone.isNotEmpty) ...[
            const SizedBox(height: 6),
            _infoRow('Telefon', phone, icon: Icons.phone_rounded),
          ],
        ],
      ),
    );
  }

  // ── No match card ─────────────────────────────────────────────────────────
  Widget _buildNoMatchCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.orange.shade50,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.orange.shade200),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.help_outline_rounded, color: Colors.orange.shade700, size: 20),
              const SizedBox(width: 8),
              Text(
                'Badan tidak dijumpai',
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 13,
                  color: Colors.orange.shade800,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'Logo ini mungkin:\n'
            '• Bukan logo halal\n'
            '• Dari badan yang belum diiktiraf JAKIM\n'
            '• Gambar kurang jelas — cuba ambil gambar yang lebih dekat dan terang',
            style: TextStyle(fontSize: 12, color: Colors.orange.shade700, height: 1.5),
          ),
        ],
      ),
    );
  }

  // ── Error card ────────────────────────────────────────────────────────────
  Widget _buildErrorCard(String msg) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.red.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.red.shade200),
      ),
      child: Text(
        msg,
        style: TextStyle(color: Colors.red.shade700, fontSize: 13),
      ),
    );
  }

  // ── Helper widgets ────────────────────────────────────────────────────────
  Widget _rowLabel(String label, IconData icon, Color color) {
    return Row(
      children: [
        Icon(icon, size: 16, color: color),
        const SizedBox(width: 6),
        Text(
          label,
          style: TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 12,
            color: color,
          ),
        ),
      ],
    );
  }

  Widget _infoRow(String key, String value, {IconData? icon}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (icon != null) ...[
          Icon(icon, size: 14, color: Colors.grey.shade500),
          const SizedBox(width: 6),
        ],
        Text(
          '$key: ',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
            color: Colors.grey.shade600,
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(fontSize: 12, color: Color(0xFF1B2A1F)),
          ),
        ),
      ],
    );
  }
}
