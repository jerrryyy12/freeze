import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/constants/app_constants.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/utils/date_utils.dart';
import '../../../data/models/ingredient.dart';
import '../../../data/repositories/ingredient_provider.dart';

class AddIngredientSheet extends StatefulWidget {
  final Ingredient? ingredient;
  final Future<void> Function(Ingredient)? onSave;
  const AddIngredientSheet({super.key, this.ingredient, this.onSave});

  @override
  State<AddIngredientSheet> createState() => _AddIngredientSheetState();
}

class _AddIngredientSheetState extends State<AddIngredientSheet> {
  final _formKey = GlobalKey<FormState>();
  late TextEditingController _nameCtrl;
  late TextEditingController _quantityCtrl;
  late TextEditingController _unitCtrl;
  late TextEditingController _memoCtrl;

  late String _category;
  late String _storageLocation;
  late DateTime _expiryDate;

  bool get _isEdit => widget.ingredient?.id != null;

  @override
  void initState() {
    super.initState();
    final ing = widget.ingredient;
    _nameCtrl = TextEditingController(text: ing?.name ?? '');
    _quantityCtrl = TextEditingController(text: ing?.quantity.toString() ?? '1');
    _unitCtrl = TextEditingController(text: ing?.unit ?? 'g');
    _memoCtrl = TextEditingController(text: ing?.memo ?? '');
    _category = ing?.category ?? '채소';
    _storageLocation = ing?.storageLocation ?? '냉장';
    _expiryDate = ing?.expiryDate ?? DateTime.now().add(const Duration(days: 7));
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _quantityCtrl.dispose();
    _unitCtrl.dispose();
    _memoCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final bottomPad = MediaQuery.of(context).viewInsets.bottom;
    final maxHeight = MediaQuery.of(context).size.height * 0.9;
    return ConstrainedBox(
      constraints: BoxConstraints(maxHeight: maxHeight),
      child: Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // 드래그 핸들
            const SizedBox(height: 12),
            Center(
              child: Container(
                width: 40, height: 4,
                decoration: BoxDecoration(
                  color: AppTheme.divider,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 12),
            // 스크롤 가능한 폼
            Flexible(
              child: SingleChildScrollView(
                padding: EdgeInsets.fromLTRB(20, 0, 20, 0),
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _isEdit ? '식재료 수정' : '식재료 추가',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      const SizedBox(height: 20),
                      TextFormField(
                        controller: _nameCtrl,
                        decoration: const InputDecoration(labelText: '식재료 이름 *'),
                        validator: (v) => v == null || v.isEmpty ? '이름을 입력하세요' : null,
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            flex: 2,
                            child: TextFormField(
                              controller: _quantityCtrl,
                              keyboardType: TextInputType.number,
                              decoration: const InputDecoration(labelText: '수량 *'),
                              validator: (v) {
                                if (v == null || v.isEmpty) return '수량 입력';
                                if (double.tryParse(v) == null) return '숫자 입력';
                                return null;
                              },
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: TextFormField(
                              controller: _unitCtrl,
                              decoration: const InputDecoration(labelText: '단위'),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      Text('카테고리', style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 4,
                        children: AppConstants.categories.skip(1).map((cat) {
                          return ChoiceChip(
                            label: Text(cat),
                            selected: _category == cat,
                            onSelected: (_) => setState(() => _category = cat),
                            selectedColor: AppTheme.secondary.withOpacity(0.2),
                          );
                        }).toList(),
                      ),
                      const SizedBox(height: 16),
                      Text('보관 위치', style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 8),
                      Row(
                        children: AppConstants.storageLocations.map((loc) {
                          return Padding(
                            padding: const EdgeInsets.only(right: 8),
                            child: ChoiceChip(
                              label: Text(loc),
                              selected: _storageLocation == loc,
                              onSelected: (_) => setState(() => _storageLocation = loc),
                              selectedColor: AppTheme.primary.withOpacity(0.2),
                            ),
                          );
                        }).toList(),
                      ),
                      const SizedBox(height: 16),
                      InkWell(
                        onTap: _pickDate,
                        child: InputDecorator(
                          decoration: const InputDecoration(
                            labelText: '유통기한 *',
                            suffixIcon: Icon(Icons.calendar_today, size: 18),
                          ),
                          child: Text(AppDateUtils.toDisplay(_expiryDate)),
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        controller: _memoCtrl,
                        decoration: const InputDecoration(labelText: '메모 (선택)'),
                        maxLines: 2,
                      ),
                      const SizedBox(height: 16),
                    ],
                  ),
                ),
              ),
            ),
            // 버튼 항상 하단 고정
            Padding(
              padding: EdgeInsets.fromLTRB(20, 8, 20, 20 + bottomPad),
              child: SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: _submit,
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                  ),
                  child: Text(_isEdit ? '수정 완료' : '추가하기'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _expiryDate,
      firstDate: DateTime.now().subtract(const Duration(days: 1)),
      lastDate: DateTime.now().add(const Duration(days: 365 * 3)),
    );
    if (picked != null) setState(() => _expiryDate = picked);
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    final ingredient = Ingredient(
      id: widget.ingredient?.id,
      name: _nameCtrl.text.trim(),
      category: _category,
      storageLocation: _storageLocation,
      quantity: double.parse(_quantityCtrl.text),
      unit: _unitCtrl.text.trim().isEmpty ? 'g' : _unitCtrl.text.trim(),
      expiryDate: _expiryDate,
      addedDate: widget.ingredient?.addedDate,
      memo: _memoCtrl.text.trim().isEmpty ? null : _memoCtrl.text.trim(),
    );

    try {
      if (widget.onSave != null) {
        // 카메라 화면에서 호출: 콜백으로 직접 처리
        await widget.onSave!(ingredient);
      } else {
        // 냉장고 화면에서 호출: Provider로 처리
        final provider = context.read<IngredientProvider>();
        if (_isEdit) {
          await provider.updateIngredient(ingredient);
        } else {
          await provider.addIngredient(ingredient);
        }
      }

      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('오류: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }
}
