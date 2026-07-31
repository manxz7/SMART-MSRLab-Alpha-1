// ============================================================
// scanner_screen.dart — Skrin Imbas Produk Halal
// ============================================================
// Skrin ni bagi user:
//   1. Ambil gambar produk (guna kamera atau galeri)
//   2. Hantar gambar ke API (POST /scan-image)
//   3. API buat OCR (baca teks dari gambar) → cari dalam database JAKIM
//   4. Papar result: HALAL ke tidak, nama syarikat, brand, tarikh luput
// ============================================================

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../main.dart'; // import ApiService, ScanResult, HalalProduct

// ─────────────────────────────────────────────
// ScannerScreen — Widget utama untuk skrin ini
//
// StatefulWidget = widget yang boleh berubah (ada state)
// Contoh state: gambar yang dipilih, loading atau tidak, result scan
// ─────────────────────────────────────────────
class ScannerScreen extends StatefulWidget {
  const ScannerScreen({super.key});

  @override
  State<ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends State<ScannerScreen> {
  // ImagePicker = plugin untuk buka kamera / galeri telefon
  final _picker = ImagePicker();

  // State variables — bila nilai ni berubah, Flutter rebuild skrin automatik
  File? _image;        // gambar yang user pilih (null = belum pilih)
  bool _loading = false; // true = tengah hantar ke API, papar loading spinner
  ScanResult? _result;  // result dari API (null = belum scan)
  String? _error;       // mesej error (null = tiada error)

  // ─────────────────────────────────────────
  // _pickImage — buka kamera atau galeri
  //
  // Parameter source:
  //   ImageSource.camera  = buka kamera
  //   ImageSource.gallery = buka galeri foto
  // ─────────────────────────────────────────
  Future<void> _pickImage(ImageSource source) async {
    final XFile? picked = await _picker.pickImage(
      source: source,
      imageQuality: 70,  // compress gambar sikit (kecil = upload lagi laju)
      maxWidth: 800,     // maximum lebar gambar dalam piksel
    );

    // Kalau user cancel (tak pilih gambar), keluar dari fungsi
    if (picked == null) return;

    // Update state: simpan gambar, clear result lama
    setState(() {
      _image = File(picked.path); // tukar path string jadi File object
      _result = null;
      _error = null;
    });
  }

  // ─────────────────────────────────────────
  // _scan — hantar gambar ke API untuk scan
  // ─────────────────────────────────────────
  Future<void> _scan() async {
    // Kalau belum pilih gambar, tunjuk peringatan
    if (_image == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Pilih gambar dahulu')),
      );
      return;
    }

    // Mula loading — papar spinner, clear error/result lama
    setState(() {
      _loading = true;
      _error = null;
      _result = null;
    });

    try {
      // Hantar gambar ke API → tunggu result
      // ApiService.scanImage() ada dalam main.dart
      final result = await ApiService.scanImage(_image!);

      // Berjaya — simpan result, stop loading
      setState(() {
        _result = result;
        _loading = false;
      });
    } catch (e) {
      // Gagal — papar error, stop loading
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  // ─────────────────────────────────────────
  // build — bina UI skrin ini
  // Flutter panggil fungsi ni setiap kali state berubah
  // ─────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = Theme.of(context).colorScheme.primary; // warna hijau app

    return Scaffold(
      // AppBar = bar atas skrin
      appBar: AppBar(
        title: const Text('Imbas Produk Halal'),
        backgroundColor: primary,
        foregroundColor: Colors.white,
      ),

      // Body = kandungan utama skrin
      // SingleChildScrollView = boleh scroll kalau kandungan panjang
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [

            // ── Info banner ──────────────────────────────
            // Kotak kecil atas: arahan guna scanner
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

            // ── Kotak preview gambar ─────────────────────
            // Kalau dah pilih gambar → papar gambar
            // Kalau belum → papar placeholder "tap untuk pilih"
            GestureDetector(
              onTap: () => _pickImage(ImageSource.gallery), // tap = buka galeri
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
                    // Ada gambar → papar gambar
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(11),
                        child: Image.file(_image!, fit: BoxFit.cover),
                      )
                    // Belum ada gambar → papar ikon placeholder
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

            // ── Butang Kamera dan Galeri ─────────────────
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

            // ── Butang Imbas ─────────────────────────────
            // Bila loading → papar spinner dalam butang + disable butang
            // Bila idle → papar ikon scanner
            ElevatedButton.icon(
              icon: _loading
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Icon(Icons.document_scanner),
              label: Text(_loading ? 'Menganalisis...' : 'Imbas & Semak Halal'),
              onPressed: _loading ? null : _scan, // null = butang disable masa loading
              style: ElevatedButton.styleFrom(
                backgroundColor: primary,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
            ),

            const SizedBox(height: 20),

            // ── Papar Error (jika ada) ───────────────────
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

            // ── Papar Result Scan (jika ada) ─────────────
            // _ScanResultCard adalah widget berasingan di bawah
            if (_result != null) _ScanResultCard(result: _result!),
          ],
        ),
      ),
    );
  }
}


// ─────────────────────────────────────────────
// _ScanResultCard — papar result scan
//
// Widget ni papar dua bahagian:
//   1. Teks yang dikesan (OCR chips) atau produk yang dikenali (AI visual)
//   2. Status HALAL + senarai rekod sijil JAKIM
// ─────────────────────────────────────────────
class _ScanResultCard extends StatelessWidget {
  final ScanResult result;
  const _ScanResultCard({required this.result});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final hasHalal = result.halalRecords.isNotEmpty;
    final isOcr = result.detectionMethod == 'ocr';
    final isVision = result.detectionMethod == 'vision';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [

        // ── Kad 1: Kaedah detection + teks dikesan ──────
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
                // Header: ikon + nama kaedah detection
                Row(
                  children: [
                    Icon(
                      isVision ? Icons.visibility : isOcr ? Icons.text_fields : Icons.image_search,
                      size: 18, color: primary,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      isVision ? 'Produk Dikenal Pasti (Visual AI)' : isOcr ? 'Teks Dikesan (OCR)' : 'Tiada Kaedah',
                      style: TextStyle(fontWeight: FontWeight.bold, color: primary, fontSize: 14),
                    ),
                  ],
                ),
                const Divider(height: 16),

                // Vision AI: papar brand + produk yang dikenali dari gambar
                if (isVision) ...[
                  if (result.visionResult != null &&
                      (result.visionResult!['brand'] ?? '').toString().isNotEmpty) ...[
                    Row(children: [
                      Text('Brand:  ', style: TextStyle(fontSize: 13, color: Colors.grey[600])),
                      Text(result.visionResult!['brand'].toString(),
                          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                    ]),
                    const SizedBox(height: 4),
                    Row(children: [
                      Text('Produk: ', style: TextStyle(fontSize: 13, color: Colors.grey[600])),
                      Text(result.visionResult!['product']?.toString() ?? '-',
                          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                    ]),
                  ] else
                    Text('Tiada maklumat brand/produk dikesan',
                        style: TextStyle(fontSize: 13, color: Colors.grey[500])),
                ],

                // OCR: papar teks yang dibaca dari label produk sebagai chips
                if (isOcr && result.ocrTexts.isNotEmpty) ...[
                  Wrap(
                    spacing: 6,
                    runSpacing: 4,
                    children: result.ocrTexts
                        .map((t) => Chip(
                              label: Text(t, style: const TextStyle(fontSize: 12)),
                              visualDensity: VisualDensity.compact,
                              backgroundColor: primary.withValues(alpha: 0.1),
                              side: BorderSide(color: primary.withValues(alpha: 0.3)),
                            ))
                        .toList(),
                  ),
                ],

                const SizedBox(height: 8),

                // Badge HALAL / Tiada Sijil
                _HalalBadge(hasHalal: hasHalal),
              ],
            ),
          ),
        ),

        const SizedBox(height: 10),

        // ── Senarai rekod sijil JAKIM ─────────────────────
        if (hasHalal) ...[
          Padding(
            padding: const EdgeInsets.only(left: 4, bottom: 6),
            child: Text(
              'Sijil Halal JAKIM (${result.halalRecords.length} rekod)',
              style: TextStyle(fontWeight: FontWeight.bold, color: primary),
            ),
          ),
          // Papar setiap rekod sebagai tile
          ...result.halalRecords.map((p) => _HalalRecordTile(product: p)),
        ] else
          // Tiada rekod → papar amaran oren
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



// ─────────────────────────────────────────────
// _HalalBadge — badge status halal
// Hijau "HALAL JAKIM" atau kelabu "? Tiada Sijil"
// ─────────────────────────────────────────────
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


// ─────────────────────────────────────────────
// _HalalRecordTile — satu rekod sijil JAKIM
//
// Papar: nama syarikat, brand, nama produk,
//        kategori, kod sijil, tarikh luput
// Tarikh luput merah = dah expire, hijau = masih sah
// ─────────────────────────────────────────────
class _HalalRecordTile extends StatelessWidget {
  final HalalProduct product;
  const _HalalRecordTile({required this.product});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        // Ikon verified kat kiri
        leading: CircleAvatar(
          backgroundColor: primary.withValues(alpha: 0.1),
          child: Icon(Icons.verified, color: primary, size: 20),
        ),
        // Nama syarikat (bold) kat atas
        title: Text(product.company, style: const TextStyle(fontWeight: FontWeight.w600)),
        // Detail lain kat bawah
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (product.brand.isNotEmpty) Text('Brand: ${product.brand}'),
            if (product.productName.isNotEmpty) Text('Produk: ${product.productName}'),
            Text('Kategori: ${product.category}'),
            Text('Kod: ${product.compCode}', style: const TextStyle(fontSize: 11)),
            Text(
              'Luput: ${product.expiry}',
              style: TextStyle(
                fontSize: 11,
                // Merah kalau dah expire, hijau kalau masih sah
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

  // Semak sama ada tarikh luput dah lepas atau belum
  // Format tarikh JAKIM: "DD/MM/YYYY" contoh "31/12/2027"
  bool _isExpired(String expiry) {
    try {
      final parts = expiry.split('/');
      if (parts.length != 3) return false;
      final d = int.parse(parts[0]);
      final m = int.parse(parts[1]);
      final y = int.parse(parts[2]);
      return DateTime(y, m, d).isBefore(DateTime.now());
    } catch (_) {
      return false; // kalau format pelik, anggap tak expire
    }
  }
}
