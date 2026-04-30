import '../../core/utils/date_utils.dart';

class Ingredient {
  final int? id;
  final String name;
  final String category;
  final String storageLocation;
  final double quantity;
  final String unit;
  final DateTime expiryDate;
  final DateTime addedDate;
  final String? imagePath;
  final String? memo;

  Ingredient({
    this.id,
    required this.name,
    required this.category,
    required this.storageLocation,
    required this.quantity,
    required this.unit,
    required this.expiryDate,
    DateTime? addedDate,
    this.imagePath,
    this.memo,
  }) : addedDate = addedDate ?? DateTime.now();

  ExpiryStatus get expiryStatus => AppDateUtils.getExpiryStatus(expiryDate);
  String get expiryLabel => AppDateUtils.getExpiryLabel(expiryDate);
  int get daysLeft => AppDateUtils.daysUntilExpiry(expiryDate);

  Map<String, dynamic> toMap() {
    return {
      if (id != null) 'id': id,
      'name': name,
      'category': category,
      'storage_location': storageLocation,
      'quantity': quantity,
      'unit': unit,
      'expiry_date': AppDateUtils.toDb(expiryDate),
      'added_date': AppDateUtils.toDb(addedDate),
      'image_path': imagePath,
      'memo': memo,
    };
  }

  factory Ingredient.fromMap(Map<String, dynamic> map) {
    return Ingredient(
      id: map['id'] as int?,
      name: map['name'] as String,
      category: map['category'] as String,
      storageLocation: map['storage_location'] as String,
      quantity: (map['quantity'] as num).toDouble(),
      unit: map['unit'] as String,
      expiryDate: AppDateUtils.fromDb(map['expiry_date'] as String),
      addedDate: AppDateUtils.fromDb(map['added_date'] as String),
      imagePath: map['image_path'] as String?,
      memo: map['memo'] as String?,
    );
  }

  Ingredient copyWith({
    int? id,
    String? name,
    String? category,
    String? storageLocation,
    double? quantity,
    String? unit,
    DateTime? expiryDate,
    DateTime? addedDate,
    String? imagePath,
    String? memo,
  }) {
    return Ingredient(
      id: id ?? this.id,
      name: name ?? this.name,
      category: category ?? this.category,
      storageLocation: storageLocation ?? this.storageLocation,
      quantity: quantity ?? this.quantity,
      unit: unit ?? this.unit,
      expiryDate: expiryDate ?? this.expiryDate,
      addedDate: addedDate ?? this.addedDate,
      imagePath: imagePath ?? this.imagePath,
      memo: memo ?? this.memo,
    );
  }
}
