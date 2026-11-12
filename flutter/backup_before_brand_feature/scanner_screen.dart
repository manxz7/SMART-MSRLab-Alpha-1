import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../main.dart';

class ScannerScreen extends StatefulWidget {
  const ScannerScreen({super.key});

  @override
  State<ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends State<ScannerScreen> {
  final _picker = ImagePicker();

  File? _image;
  bool _loading = false;
  ScanResult? _result;
  String? _error;

  Future<void> _pickImage(ImageSource source) async {
    final XFile? picked = await _picker.pickImage(
      source: source,
      imageQuality: 70,   // smaller file = faster upload over mobile data
      maxWidth: 800,
    );
    if (picked == null) return;
    setState(() {
      _image = File(picked.path);
      _result = null;
      _error = null;
    });
  }

  Future<void> _scan() async {
    if (_image == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Pilih gambar dahulu')),
      );
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _result = null;
    });

    try {
      final result = await ApiService.scanImage(_image!);
      setState(() {
        _result = result;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = Theme.of(context).colorScheme.primary;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Imbas Produk Halal'),
        backgroundColor: primary,
        foregroundColor: Colors.white,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: primary.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: primary.withValues(alpha: 0.3)),
              ),
              child: Row(
                children: [
                  Icon(Icons.info_outline, size: 16, color: primary),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      "Imbas label produk untuk semak status halal JAKIM",
                      style: TextStyle(fontSize: 12, color: primary),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 14),
            GestureDetector(
              onTap: () => _pickImage(ImageSource.gallery),
              child: Container(
                height: 240,
                decoration: BoxDecoration(
                  color: isDark ? const Color(0xFF2A2A2A) : const Color(0xFFEEEEEE),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: _image != null ? primary : Colors.grey.shade400,
                    width: _image != null ? 2 : 1,
                  ),
                ),
                child: _image != null
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(11),
                        child: Image.file(_image!, fit: BoxFit.cover),
                      )
                    : Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.image_outlined, size: 56, color: Colors.grey[500]),
                          const SizedBox(height: 8),
                          Text(
                            'Tap untuk pilih dari galeri',
                            style: TextStyle(color: Colors.grey[500]),
                          ),
                        ],
                      ),
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.camera_alt),
                    label: const Text('Kamera'),
                    onPressed: () => _pickImage(ImageSource.camera),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: primary,
                      side: BorderSide(color: primary),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.photo_library),
                    label: const Text('Galeri'),
                    onPressed: () => _pickImage(ImageSource.gallery),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: primary,
                      side: BorderSide(color: primary),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              icon: _loading
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Icon(Icons.document_scanner),
              label: Text(_loading ? 'Menganalisis...' : 'Imbas & Semak Halal'),
              onPressed: _loading ? null : _scan,
              style: ElevatedButton.styleFrom(
                backgroundColor: primary,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
            ),
            const SizedBox(height: 20),
            if (_error != null)
              Card(
                color: Colors.red.shade50,
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.error_outline, color: Colors.red),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(_error!, style: const TextStyle(color: Colors.red)),
                      ),
                    ],
                  ),
                ),
              ),
            if (_result != null) _ScanResultCard(result: _result!),
          ],
        ),
      ),
    );
  }
}

class _ScanResultCard extends StatelessWidget {
  final ScanResult result;
  const _ScanResultCard({required this.result});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final hasHalal = result.halalRecords.isNotEmpty;
    final isOcr = result.detectionMethod == 'ocr';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Card(
          elevation: 2,
          color: isDark ? const Color(0xFF1E2E1E) : const Color(0xFFF0FFF4),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
            side: BorderSide(color: primary.withValues(alpha: 0.3)),
          ),
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(isOcr ? Icons.text_fields : Icons.image_search,
                        size: 18, color: primary),
                    const SizedBox(width: 6),
                    Text(
                      isOcr ? 'Teks Dikesan (OCR)' : 'Produk Dikenal Pasti (Visual AI)',
                      style: TextStyle(
                          fontWeight: FontWeight.bold, color: primary, fontSize: 14),
                    ),
                  ],
                ),
                const Divider(height: 16),
                // OCR: show extracted texts
                if (isOcr && result.ocrTexts.isNotEmpty) ...[
                  Wrap(
                    spacing: 6,
                    runSpacing: 4,
                    children: result.ocrTexts
                        .map((t) => Chip(
                              label: Text(t,
                                  style: const TextStyle(fontSize: 12)),
                              visualDensity: VisualDensity.compact,
                              backgroundColor:
                                  primary.withValues(alpha: 0.1),
                              side: BorderSide(
                                  color: primary.withValues(alpha: 0.3)),
                            ))
                        .toList(),
                  ),
                ],
                // EfficientNet: show matched product name + confidence
                if (!isOcr && result.matchedProduct != null) ...[
                  Text(result.matchedProduct!,
                      style: const TextStyle(
                          fontSize: 16, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 4),
                  if (result.matchedCategory != null)
                    Text(result.matchedCategory!,
                        style: TextStyle(fontSize: 12, color: Colors.grey[600])),
                  const SizedBox(height: 8),
                  if (result.confidence != null)
                    _ConfidenceBadge(
                      pct: (result.confidence! * 100).toStringAsFixed(1),
                      isHigh: result.confidence! >= 0.7,
                    ),
                ],
                const SizedBox(height: 8),
                _HalalBadge(hasHalal: hasHalal),
              ],
            ),
          ),
        ),
        const SizedBox(height: 10),
        if (hasHalal) ...[
          Padding(
            padding: const EdgeInsets.only(left: 4, bottom: 6),
            child: Text(
              'Sijil Halal JAKIM (${result.halalRecords.length} rekod)',
              style: TextStyle(fontWeight: FontWeight.bold, color: primary),
            ),
          ),
          ...result.halalRecords.map((p) => _HalalRecordTile(product: p)),
        ] else
          Card(
            color: Colors.orange.shade50,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  Icon(Icons.warning_amber_rounded, color: Colors.orange.shade700),
                  const SizedBox(width: 8),
                  const Expanded(
                    child: Text(
                      'Tiada rekod halal JAKIM ditemui untuk produk ini.\n'
                      'Ini tidak bermakna produk tidak halal — mungkin tiada dalam pangkalan data.',
                      style: TextStyle(fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

class _ConfidenceBadge extends StatelessWidget {
  final String pct;
  final bool isHigh;
  const _ConfidenceBadge({required this.pct, required this.isHigh});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: isHigh ? Colors.green.shade100 : Colors.orange.shade100,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        'Keyakinan: $pct%',
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.bold,
          color: isHigh ? Colors.green.shade800 : Colors.orange.shade800,
        ),
      ),
    );
  }
}

class _HalalBadge extends StatelessWidget {
  final bool hasHalal;
  const _HalalBadge({required this.hasHalal});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: hasHalal ? const Color(0xFF1B7A4B) : Colors.grey.shade300,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        hasHalal ? 'HALAL JAKIM' : '? Tiada Sijil',
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.bold,
          color: hasHalal ? Colors.white : Colors.grey.shade700,
        ),
      ),
    );
  }
}

class _HalalRecordTile extends StatelessWidget {
  final HalalProduct product;
  const _HalalRecordTile({required this.product});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: primary.withValues(alpha: 0.1),
          child: Icon(Icons.verified, color: primary, size: 20),
        ),
        title: Text(product.company, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (product.brand.isNotEmpty) Text('Brand: ${product.brand}'),
            Text('Kategori: ${product.category}'),
            Text('Kod: ${product.compCode}', style: const TextStyle(fontSize: 11)),
            Text(
              'Luput: ${product.expiry}',
              style: TextStyle(
                fontSize: 11,
                color: _isExpired(product.expiry) ? Colors.red : Colors.green.shade700,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
        isThreeLine: true,
      ),
    );
  }

  bool _isExpired(String expiry) {
    try {
      final parts = expiry.split('/');
      if (parts.length != 3) return false;
      final d = int.parse(parts[0]);
      final m = int.parse(parts[1]);
      final y = int.parse(parts[2]);
      return DateTime(y, m, d).isBefore(DateTime.now());
    } catch (_) {
      return false;
    }
  }
}
