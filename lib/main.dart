// ============================================================
// main.dart — SMART SandboxPlayground (Super App for Muslims)
// ============================================================
// Unified entry point merging the Halal Checker and Qibla App.
// ============================================================

import 'dart:convert';           // For decoding JSON responses
import 'dart:io';                // For File type
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;  // For making API calls
import 'package:image_picker/image_picker.dart';  // For camera/gallery
import 'package:shared_preferences/shared_preferences.dart';
import 'package:timezone/data/latest.dart' as tz;

import 'screens/home_screen.dart';
import 'services/notification_service.dart';

// ─────────────────────────────────────────────
// API CONFIGURATION
// Change this IP to your computer's WiFi IP address
// so your phone can reach the API.
//
// If testing on Windows desktop, use: http://localhost:8000
// If testing on Android phone, use your WiFi IP: http://192.168.1.10:8000
// ─────────────────────────────────────────────
/// Reads the API server URL from settings at call time.
/// Defaults to the local network address; override in Settings → API Server.
String get apiBaseUrl =>
    gPrefs.getString('api_url') ??
    'https://halal-jakim-backend-vfhol5-85579f-159-65-136-40.sslip.io';

// ─────────────────────────────────────────────
// GLOBAL SHARED PREFERENCES
// Loaded once at startup. All screens import this
// from main.dart so they can read/write settings
// without async boilerplate.
// ─────────────────────────────────────────────
late SharedPreferences gPrefs;


// ─────────────────────────────────────────────
// ENTRY POINT
// Every Flutter app starts here — runApp() launches the app
// ─────────────────────────────────────────────
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  tz.initializeTimeZones();           // must happen before any tz.local access
  gPrefs = await SharedPreferences.getInstance();

  // Migrate: clear old ngrok/local URL so new server default takes effect
  final savedUrl = gPrefs.getString('api_url') ?? '';
  if (savedUrl.contains('ngrok') || savedUrl.contains('192.168') || savedUrl.contains('careerlab.my')) {
    await gPrefs.remove('api_url');
  }

  await initNotifications();          // initializes flutter_local_notifications
  runApp(const SmartApp());
}

// ─────────────────────────────────────────────
// ROOT APP WIDGET
// ─────────────────────────────────────────────
class SmartApp extends StatelessWidget {
  const SmartApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SMART SandboxPlayground',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF1B7A4B),  // Islamic green
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}


// ─────────────────────────────────────────────
// DATA MODEL — HalalProduct
// A "model" is just a class that represents one
// record from the database. Each product has these fields.
// ─────────────────────────────────────────────
class HalalProduct {
  final int id;
  final String category;
  final String compCode;
  final String company;
  final String brand;
  final String productName; // nama produk (hasil NER cleaning)
  final String address;
  final String expiry;

  HalalProduct({
    required this.id,
    required this.category,
    required this.compCode,
    required this.company,
    required this.brand,
    required this.productName,
    required this.address,
    required this.expiry,
  });

  // fromJson: tukar JSON dari API jadi objek HalalProduct
  // API hantar JSON: {"id": 1, "company": "Nestle", "product_name": "MILO", ...}
  // Fungsi ni tukar JSON tu jadi objek Dart yang boleh dipakai dalam app
  factory HalalProduct.fromJson(Map<String, dynamic> json) {
    return HalalProduct(
      id:          json['id'] ?? 0,
      category:    json['category'] ?? '',
      compCode:    json['comp_code'] ?? '',
      company:     json['company'] ?? '',
      brand:       json['brand'] ?? '',
      productName: json['product_name'] ?? '',
      address:     json['address'] ?? '',
      expiry:      json['expiry'] ?? '',
    );
  }
}


// ─────────────────────────────────────────────
// DATA MODEL — ScanResult
// Returned by POST /scan-image.
// Contains the matched product name, confidence
// score, and any JAKIM halal records found.
// ─────────────────────────────────────────────
class ScanResult {
  final List<String> ocrTexts;
  final String? matchedProduct;
  final String? matchedCategory;
  final double? confidence;
  final String detectionMethod;
  final List<HalalProduct> halalRecords;
  final Map<String, dynamic>? visionResult;

  ScanResult({
    required this.ocrTexts,
    required this.matchedProduct,
    required this.matchedCategory,
    required this.confidence,
    required this.detectionMethod,
    required this.halalRecords,
    this.visionResult,
  });

  factory ScanResult.fromJson(Map<String, dynamic> json) {
    final List records = json['halal_records'] ?? [];
    final List ocr = json['ocr_texts'] ?? [];
    return ScanResult(
      ocrTexts:        ocr.map((e) => e.toString()).toList(),
      matchedProduct:  json['matched_product'] as String?,
      matchedCategory: json['matched_category'] as String?,
      confidence:      json['confidence'] != null ? (json['confidence'] as num).toDouble() : null,
      detectionMethod: json['detection_method'] ?? 'none',
      halalRecords:    records.map((r) => HalalProduct.fromJson(r)).toList(),
      visionResult:    json['vision_result'] != null ? Map<String, dynamic>.from(json['vision_result']) : null,
    );
  }
}


// ─────────────────────────────────────────────
// DATA MODEL — CompanyResult
// Satu syarikat dalam hasil carian grouped.
// Mengandungi nama syarikat, bilangan produk,
// dan senarai semua produk mereka.
// ─────────────────────────────────────────────
class CompanyResult {
  final String company;
  final int productCount;
  final int popularityScore;
  final List<String> categories;
  final List<HalalProduct> products;

  CompanyResult({
    required this.company,
    required this.productCount,
    required this.popularityScore,
    required this.categories,
    required this.products,
  });

  factory CompanyResult.fromJson(Map<String, dynamic> json) {
    final List prods = json['products'] ?? [];
    return CompanyResult(
      company:         json['company'] ?? '',
      productCount:    json['product_count'] ?? 0,
      popularityScore: json['popularity_score'] ?? 0,
      categories:      List<String>.from(json['categories'] ?? []),
      products:        prods.map((p) => HalalProduct.fromJson(p)).toList(),
    );
  }
}


// ─────────────────────────────────────────────
// API SERVICE
// All the functions that call our FastAPI backend live here.
// Separating API logic from UI logic keeps the code clean.
// ─────────────────────────────────────────────
class ApiService {
  /// Search products by keyword (calls GET /search?q=...)
  static Future<List<HalalProduct>> search(String query) async {
    final uri = Uri.parse('$apiBaseUrl/search').replace(
      queryParameters: {'q': query, 'limit': '50'},
    );

    final response = await http.get(uri);

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      final List results = data['results'];
      return results.map((item) => HalalProduct.fromJson(item)).toList();
    } else {
      throw Exception('Search failed: ${response.statusCode}');
    }
  }

  /// Search companies grouped by name (calls GET /search-grouped?q=...)
  static Future<List<CompanyResult>> searchGrouped(String query) async {
    final uri = Uri.parse('$apiBaseUrl/search-grouped').replace(
      queryParameters: {'q': query, 'limit': '100'},
    );
    final response = await http.get(uri);
    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      final List companies = data['companies'];
      return companies.map((c) => CompanyResult.fromJson(c)).toList();
    } else {
      throw Exception('Search failed: ${response.statusCode}');
    }
  }

  /// Scan product image (calls POST /scan-image)
  static Future<ScanResult> scanImage(File imageFile) async {
    final uri = Uri.parse('$apiBaseUrl/scan-image');
    final request = http.MultipartRequest('POST', uri);
    // Read bytes directly — avoids Android content-URI/path issues
    final bytes = await imageFile.readAsBytes();
    request.files.add(http.MultipartFile.fromBytes(
      'file',
      bytes,
      filename: 'scan.jpg',
    ));
    // 120-second timeout — covers slow mobile upload + EfficientNet inference
    final streamed = await request.send().timeout(const Duration(seconds: 120));
    final response = await http.Response.fromStream(streamed);
    if (response.statusCode == 200) {
      return ScanResult.fromJson(jsonDecode(response.body));
    } else if (response.statusCode == 503) {
      throw Exception('AI model not ready. Run build_embeddings.py first.');
    } else {
      throw Exception('Scan failed ${response.statusCode}: ${response.body}');
    }
  }
}


// ─────────────────────────────────────────────
// SEARCH SCREEN
// The main screen of the app.
// Has a search bar at the top and a results list below.
//
// "StatefulWidget" means this screen can change its appearance
// based on data (e.g. show loading spinner, then show results).
// Compare to StatelessWidget which never changes after it's built.
// ─────────────────────────────────────────────
class SearchScreen extends StatefulWidget {
  const SearchScreen({super.key});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  final TextEditingController _controller = TextEditingController();
  List<CompanyResult> _results = [];  // senarai syarikat (grouped)
  bool _isLoading = false;
  String _errorMessage = '';
  bool _hasSearched = false;

  Future<void> _doSearch() async {
    final query = _controller.text.trim();
    if (query.isEmpty) return;

    setState(() {
      _isLoading = true;
      _errorMessage = '';
      _hasSearched = true;
    });

    try {
      final results = await ApiService.searchGrouped(query);
      setState(() {
        _results = results;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'Could not connect to server.\nMake sure the API is running.';
        _isLoading = false;
        _results = [];
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // AppBar = the top bar of the screen
      appBar: AppBar(
        backgroundColor: const Color(0xFF1B7A4B),
        title: const Text(
          'Halal Checker',
          style: TextStyle(
            color: Colors.white,
            fontWeight: FontWeight.bold,
            fontSize: 20,
          ),
        ),
        centerTitle: true,
        actions: [
          // Camera scan button in top-right corner
          IconButton(
            icon: const Icon(Icons.camera_alt, color: Colors.white),
            tooltip: 'Scan product photo',
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const ScanScreen()),
              );
            },
          ),
        ],
      ),

      // Body = everything below the AppBar
      body: Column(
        children: [
          // ── Search Bar ──────────────────────────────
          Container(
            color: const Color(0xFF1B7A4B),
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: Row(
              children: [
                // Text input field
                Expanded(
                  child: TextField(
                    controller: _controller,
                    onSubmitted: (_) => _doSearch(),
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      hintText: 'Search product or company...',
                      hintStyle: TextStyle(color: Colors.white.withValues(alpha: 0.7)),
                      filled: true,
                      fillColor: Colors.white.withValues(alpha: 0.2),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide.none,
                      ),
                      prefixIcon: const Icon(Icons.search, color: Colors.white),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                // Search button
                ElevatedButton(
                  onPressed: _doSearch,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.white,
                    foregroundColor: const Color(0xFF1B7A4B),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  child: const Text('Search', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ],
            ),
          ),

          // ── Results Area ─────────────────────────────
          Expanded(
            child: _buildBody(),
          ),
        ],
      ),
    );
  }

  /// Decides what to show in the results area based on current state
  Widget _buildBody() {
    // Show loading spinner while waiting for API
    if (_isLoading) {
      return const Center(
        child: CircularProgressIndicator(color: Color(0xFF1B7A4B)),
      );
    }

    // Show error message if API call failed
    if (_errorMessage.isNotEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.wifi_off, size: 64, color: Colors.grey),
              const SizedBox(height: 16),
              Text(
                _errorMessage,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.grey, fontSize: 16),
              ),
            ],
          ),
        ),
      );
    }

    // Show "no results" message
    if (_hasSearched && _results.isEmpty) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.search_off, size: 64, color: Colors.grey),
            SizedBox(height: 16),
            Text(
              'No halal-certified records found.',
              style: TextStyle(color: Colors.grey, fontSize: 16),
            ),
          ],
        ),
      );
    }

    // Show welcome screen before first search
    if (!_hasSearched) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.verified_rounded, size: 80, color: Colors.green.shade200),
            const SizedBox(height: 16),
            const Text(
              'Search for a product or company\nto check halal status',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey, fontSize: 16),
            ),
            const SizedBox(height: 8),
            Text(
              '348,844 certified records from JAKIM',
              style: TextStyle(color: Colors.grey.shade400, fontSize: 13),
            ),
          ],
        ),
      );
    }

    // Show result count + company list
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: Text(
            '${_results.length} syarikat ditemui',
            style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
          ),
        ),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            itemCount: _results.length,
            itemBuilder: (context, index) {
              return CompanyCard(
                company: _results[index],
                query: _controller.text.trim(),
              );
            },
          ),
        ),
      ],
    );
  }
}


// ─────────────────────────────────────────────
// PRODUCT CARD
// One card in the search results list.
// Shows a summary of one halal product/company.
// Tapping it opens the DetailScreen.
// ─────────────────────────────────────────────
class ProductCard extends StatelessWidget {
  final HalalProduct product;
  const ProductCard({super.key, required this.product});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 5),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () {
          // Navigate to detail screen when card is tapped
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => DetailScreen(product: product),
            ),
          );
        },
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Top row: company name + HALAL badge
              Row(
                children: [
                  Expanded(
                    child: Text(
                      product.company,
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 15,
                      ),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: Colors.green.shade50,
                      border: Border.all(color: Colors.green.shade300),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      'HALAL ✓',
                      style: TextStyle(
                        color: Colors.green.shade700,
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ],
              ),

              // Nama produk (jika ada)
              if (product.productName.isNotEmpty) ...[
                const SizedBox(height: 2),
                Text(
                  product.productName,
                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],

              // Nama brand (jika ada)
              if (product.brand.isNotEmpty) ...[
                const SizedBox(height: 2),
                Text(
                  product.brand,
                  style: TextStyle(color: Colors.grey.shade700, fontSize: 13),
                ),
              ],

              const SizedBox(height: 8),

              // Bottom row: category chip + expiry
              Row(
                children: [
                  // Category chip
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: const Color(0xFF1B7A4B).withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      product.category,
                      style: const TextStyle(
                        color: Color(0xFF1B7A4B),
                        fontSize: 11,
                      ),
                    ),
                  ),
                  const Spacer(),
                  // Expiry date
                  Icon(Icons.calendar_today, size: 12, color: Colors.grey.shade400),
                  const SizedBox(width: 4),
                  Text(
                    product.expiry.isNotEmpty ? 'Exp: ${product.expiry}' : 'No expiry',
                    style: TextStyle(color: Colors.grey.shade500, fontSize: 12),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}


// ─────────────────────────────────────────────
// COMPANY CARD
// Kad syarikat dalam hasil carian.
// Papar nama syarikat (bold), bilangan produk,
// badge "Popular" kalau syarikat terkenal.
// Tap → buka CompanyProductsScreen
// ─────────────────────────────────────────────
class CompanyCard extends StatelessWidget {
  final CompanyResult company;
  final String query;
  const CompanyCard({super.key, required this.company, required this.query});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 5),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => CompanyProductsScreen(
                company: company,
                query: query,
              ),
            ),
          );
        },
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Baris atas: nama syarikat + badge HALAL
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      company.company,
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: Colors.green.shade50,
                      border: Border.all(color: Colors.green.shade300),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      'HALAL ✓',
                      style: TextStyle(
                        color: Colors.green.shade700,
                        fontSize: 11,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              // Baris tengah: bilangan produk + badge Popular
              Row(
                children: [
                  Icon(Icons.inventory_2_outlined, size: 14, color: Colors.grey.shade600),
                  const SizedBox(width: 4),
                  Text(
                    '${company.productCount} produk bersijil',
                    style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
                  ),
                  if (company.popularityScore > 0) ...[
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.amber.shade50,
                        border: Border.all(color: Colors.amber.shade300),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.star, size: 10, color: Colors.amber.shade700),
                          const SizedBox(width: 2),
                          Text(
                            'Popular',
                            style: TextStyle(
                              fontSize: 10,
                              color: Colors.amber.shade700,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
              const SizedBox(height: 6),
              // Baris bawah: kategori chips + anak panah
              Row(
                children: [
                  Expanded(
                    child: Wrap(
                      spacing: 4,
                      children: company.categories
                          .map((cat) => Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(
                                  color: const Color(0xFF1B7A4B).withValues(alpha: 0.08),
                                  borderRadius: BorderRadius.circular(20),
                                ),
                                child: Text(
                                  cat,
                                  style: const TextStyle(color: Color(0xFF1B7A4B), fontSize: 11),
                                ),
                              ))
                          .toList(),
                    ),
                  ),
                  Icon(Icons.chevron_right, color: Colors.grey.shade400),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}


// ─────────────────────────────────────────────
// COMPANY PRODUCTS SCREEN
// Papar semua produk halal untuk satu syarikat.
// Dibuka bila user tap CompanyCard.
// Fetch produk secara lazy dari /company-products
// supaya search results asal tetap laju.
// ─────────────────────────────────────────────
class CompanyProductsScreen extends StatefulWidget {
  final CompanyResult company;
  final String query;
  const CompanyProductsScreen({
    super.key,
    required this.company,
    required this.query,
  });

  @override
  State<CompanyProductsScreen> createState() => _CompanyProductsScreenState();
}

class _CompanyProductsScreenState extends State<CompanyProductsScreen> {
  List<HalalProduct> _products = [];
  bool _isLoading = true;
  String _error = '';

  @override
  void initState() {
    super.initState();
    _fetchProducts();
  }

  // Panggil API /company-products apabila screen dibuka
  Future<void> _fetchProducts() async {
    try {
      final uri = Uri.parse('$apiBaseUrl/company-products').replace(
        queryParameters: {
          'company': widget.company.company,
          'q': widget.query,
        },
      );
      final response = await http.get(uri);
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final list = (data['products'] as List)
            .map((e) => HalalProduct.fromJson(e as Map<String, dynamic>))
            .toList();
        setState(() {
          _products = list;
          _isLoading = false;
        });
      } else {
        setState(() {
          _error = 'Ralat server: ${response.statusCode}';
          _isLoading = false;
        });
      }
    } catch (e) {
      setState(() {
        _error = 'Tidak dapat sambung ke server.';
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: const Color(0xFF1B7A4B),
        iconTheme: const IconThemeData(color: Colors.white),
        title: Text(
          widget.company.company,
          style: const TextStyle(
            color: Colors.white,
            fontWeight: FontWeight.bold,
            fontSize: 14,
          ),
          overflow: TextOverflow.ellipsis,
        ),
      ),
      body: Column(
        children: [
          // Header: jumlah produk
          Container(
            width: double.infinity,
            color: const Color(0xFF1B7A4B).withValues(alpha: 0.06),
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
            child: Row(
              children: [
                Icon(Icons.verified_rounded, color: Colors.green.shade600, size: 18),
                const SizedBox(width: 8),
                Text(
                  '${widget.company.productCount} produk halal bersijil JAKIM',
                  style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13),
                ),
              ],
            ),
          ),
          // Senarai produk — loading / error / list
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator(color: Color(0xFF1B7A4B)))
                : _error.isNotEmpty
                    ? Center(child: Text(_error, style: const TextStyle(color: Colors.red)))
                    : _products.isEmpty
                        ? const Center(child: Text('Tiada produk ditemui.'))
                        : ListView.builder(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            itemCount: _products.length,
                            itemBuilder: (context, index) {
                              return ProductCard(product: _products[index]);
                            },
                          ),
          ),
        ],
      ),
    );
  }
}


// ─────────────────────────────────────────────
// DETAIL SCREEN
// Shows FULL information for one product.
// Opened when user taps a ProductCard.
// ─────────────────────────────────────────────
class DetailScreen extends StatelessWidget {
  final HalalProduct product;
  const DetailScreen({super.key, required this.product});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: const Color(0xFF1B7A4B),
        iconTheme: const IconThemeData(color: Colors.white),
        title: const Text(
          'Halal Details',
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Green HALAL banner at top
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: const Color(0xFF1B7A4B),
                borderRadius: BorderRadius.circular(16),
              ),
              child: const Column(
                children: [
                  Icon(Icons.verified_rounded, color: Colors.white, size: 48),
                  SizedBox(height: 8),
                  Text(
                    'HALAL CERTIFIED',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 2,
                    ),
                  ),
                  Text(
                    'JAKIM — Jabatan Kemajuan Islam Malaysia',
                    style: TextStyle(color: Colors.white70, fontSize: 12),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 24),

            // Detail rows
            _detailRow(Icons.business, 'Syarikat', product.company),
            if (product.brand.isNotEmpty)
              _detailRow(Icons.label, 'Brand', product.brand),
            if (product.productName.isNotEmpty)
              _detailRow(Icons.inventory_2, 'Produk', product.productName),
            _detailRow(Icons.category, 'Kategori', product.category),
            _detailRow(Icons.badge, 'Certificate Code', product.compCode),
            _detailRow(Icons.calendar_month, 'Expiry Date',
                product.expiry.isNotEmpty ? product.expiry : 'Not specified'),
            _detailRow(Icons.location_on, 'Address',
                product.address.isNotEmpty ? product.address : 'Not specified'),
          ],
        ),
      ),
    );
  }

  /// Helper to build a labelled detail row with an icon
  Widget _detailRow(IconData icon, String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: const Color(0xFF1B7A4B), size: 22),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    color: Colors.grey,
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: const TextStyle(fontSize: 15),
                ),
                const Divider(height: 20),
              ],
            ),
          ),
        ],
      ),
    );
  }
}


// ─────────────────────────────────────────────
// SCAN SCREEN
// User picks a photo from camera or gallery.
// The image is sent to POST /scan-image.
// Shows the matched product name + halal status.
// ─────────────────────────────────────────────
class ScanScreen extends StatefulWidget {
  const ScanScreen({super.key});

  @override
  State<ScanScreen> createState() => _ScanScreenState();
}

class _ScanScreenState extends State<ScanScreen> {
  File? _pickedImage;
  bool _isLoading = false;
  ScanResult? _result;
  String _errorMessage = '';

  final ImagePicker _picker = ImagePicker();

  Future<void> _pickImage(ImageSource source) async {
    final XFile? picked = await _picker.pickImage(
      source: source,
      imageQuality: 85,
      maxWidth: 1024,
    );
    if (picked == null) return;

    setState(() {
      _pickedImage = File(picked.path);
      _result = null;
      _errorMessage = '';
    });

    await _doScan(File(picked.path));
  }

  Future<void> _doScan(File imageFile) async {
    setState(() {
      _isLoading = true;
      _errorMessage = '';
    });

    try {
      final result = await ApiService.scanImage(imageFile);
      setState(() {
        _result = result;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        // Show full error so we can debug what's going wrong
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: const Color(0xFF1B7A4B),
        iconTheme: const IconThemeData(color: Colors.white),
        title: const Text(
          'Scan Product',
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // ── Pick image buttons ─────────────────────
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    icon: const Icon(Icons.camera_alt),
                    label: const Text('Camera'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF1B7A4B),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    onPressed: () => _pickImage(ImageSource.camera),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton.icon(
                    icon: const Icon(Icons.photo_library),
                    label: const Text('Gallery'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.white,
                      foregroundColor: const Color(0xFF1B7A4B),
                      side: const BorderSide(color: Color(0xFF1B7A4B)),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    onPressed: () => _pickImage(ImageSource.gallery),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 20),

            // ── Preview picked image ───────────────────
            if (_pickedImage != null)
              ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: Image.file(
                  _pickedImage!,
                  height: 220,
                  fit: BoxFit.cover,
                ),
              ),

            const SizedBox(height: 20),

            // ── Loading spinner ────────────────────────
            if (_isLoading)
              const Center(
                child: Column(
                  children: [
                    CircularProgressIndicator(color: Color(0xFF1B7A4B)),
                    SizedBox(height: 12),
                    Text('Analysing product...', style: TextStyle(color: Colors.grey)),
                  ],
                ),
              ),

            // ── Error message ──────────────────────────
            if (_errorMessage.isNotEmpty)
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.red.shade50,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.red.shade200),
                ),
                child: Text(_errorMessage, style: TextStyle(color: Colors.red.shade700)),
              ),

            // ── Scan result ────────────────────────────
            if (_result != null) _buildResult(_result!),

            // ── Placeholder when no image yet ─────────
            if (_pickedImage == null && !_isLoading)
              Container(
                height: 200,
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.grey.shade300, style: BorderStyle.solid),
                ),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.camera_alt_outlined, size: 56, color: Colors.grey.shade400),
                    const SizedBox(height: 12),
                    Text(
                      'Take or choose a photo of the product\nlabel to check its halal status',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.grey.shade500),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildResult(ScanResult result) {
    final bool hasHalal = result.halalRecords.isNotEmpty;
    final bool isVision = result.detectionMethod == 'vision';
    final bool isOcr = result.detectionMethod == 'ocr';
    final int pct = ((result.confidence ?? 0.0) * 100).round();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Confidence + matched product
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.blue.shade50,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: Colors.blue.shade100),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    isVision ? Icons.visibility : isOcr ? Icons.text_fields : Icons.image_search,
                    color: Colors.blue,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    isVision ? 'Produk Dikenal Pasti (Visual AI)' : isOcr ? 'Teks dikesan (OCR)' : 'Best visual match',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                  const Spacer(),
                  if (!isOcr && !isVision)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: pct >= 70 ? Colors.green.shade100 : Colors.orange.shade100,
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        '$pct% match',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: pct >= 70 ? Colors.green.shade700 : Colors.orange.shade700,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              if (isVision) ...[
                if (result.visionResult != null && (result.visionResult!['brand'] ?? '').toString().isNotEmpty) ...[
                  Row(children: [
                    Text('Brand: ', style: TextStyle(fontSize: 13, color: Colors.grey.shade600)),
                    Text(result.visionResult!['brand'].toString(), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                  ]),
                  const SizedBox(height: 2),
                  Row(children: [
                    Text('Produk: ', style: TextStyle(fontSize: 13, color: Colors.grey.shade600)),
                    Text(result.visionResult!['product']?.toString() ?? '-', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                  ]),
                ] else ...[
                  Text('Tiada maklumat brand/produk dikesan', style: TextStyle(fontSize: 13, color: Colors.grey.shade500)),
                ],
              ] else if (isOcr && result.ocrTexts.isNotEmpty)
                Wrap(
                  spacing: 6,
                  children: result.ocrTexts
                      .map((t) => Chip(
                            label: Text(t, style: const TextStyle(fontSize: 12)),
                            visualDensity: VisualDensity.compact,
                          ))
                      .toList(),
                )
              else if (result.matchedProduct != null) ...[
                Text(result.matchedProduct!, style: const TextStyle(fontSize: 15)),
                Text(result.matchedCategory ?? '',
                    style: TextStyle(color: Colors.grey.shade600, fontSize: 13)),
              ],
            ],
          ),
        ),

        const SizedBox(height: 16),

        // JAKIM halal status
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: hasHalal ? Colors.green.shade50 : Colors.red.shade50,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: hasHalal ? Colors.green.shade200 : Colors.red.shade200,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    hasHalal ? Icons.verified_rounded : Icons.cancel_rounded,
                    color: hasHalal ? Colors.green.shade700 : Colors.red.shade700,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    hasHalal ? 'HALAL CERTIFIED (JAKIM)' : 'Not found in JAKIM database',
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      color: hasHalal ? Colors.green.shade700 : Colors.red.shade700,
                    ),
                  ),
                ],
              ),
              if (hasHalal) ...[
                const SizedBox(height: 12),
                ...result.halalRecords.take(3).map((p) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: InkWell(
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => DetailScreen(product: p)),
                    ),
                    child: Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: Colors.green.shade100),
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(p.company, style: const TextStyle(fontWeight: FontWeight.w600)),
                                if (p.brand.isNotEmpty)
                                  Text(p.brand, style: TextStyle(color: Colors.grey.shade600, fontSize: 12)),
                                Text('Exp: ${p.expiry.isNotEmpty ? p.expiry : "N/A"}',
                                    style: TextStyle(color: Colors.grey.shade500, fontSize: 11)),
                              ],
                            ),
                          ),
                          Icon(Icons.chevron_right, color: Colors.grey.shade400),
                        ],
                      ),
                    ),
                  ),
                )),
              ] else ...[
                const SizedBox(height: 8),
                Text(
                  'The matched product was not found in JAKIM\'s halal database. '
                  'This does not necessarily mean it is haram — try searching by company name.',
                  style: TextStyle(color: Colors.red.shade700, fontSize: 13),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

