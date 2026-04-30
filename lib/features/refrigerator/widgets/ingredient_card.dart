import 'package:flutter/material.dart';
import 'package:flutter_slidable/flutter_slidable.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/utils/date_utils.dart';
import '../../../data/models/ingredient.dart';
import '../../../data/repositories/ingredient_provider.dart';
import 'add_ingredient_sheet.dart';

class IngredientCard extends StatelessWidget {
  final Ingredient ingredient;
  const IngredientCard({super.key, required this.ingredient});

  Color get _expiryColor {
    return switch (ingredient.expiryStatus) {
      ExpiryStatus.expired => AppTheme.error,
      ExpiryStatus.danger => AppTheme.error,
      ExpiryStatus.warning => AppTheme.warning,
      ExpiryStatus.good => AppTheme.secondary,
    };
  }

  @override
  Widget build(BuildContext context) {
    return Slidable(
      endActionPane: ActionPane(
        motion: const DrawerMotion(),
        children: [
          SlidableAction(
            onPressed: (_) => _edit(context),
            backgroundColor: AppTheme.primary,
            foregroundColor: Colors.white,
            icon: Icons.edit,
            label: '수정',
            borderRadius: const BorderRadius.only(
              topLeft: Radius.circular(12),
              bottomLeft: Radius.circular(12),
            ),
          ),
          SlidableAction(
            onPressed: (_) => _delete(context),
            backgroundColor: AppTheme.error,
            foregroundColor: Colors.white,
            icon: Icons.delete,
            label: '삭제',
            borderRadius: const BorderRadius.only(
              topRight: Radius.circular(12),
              bottomRight: Radius.circular(12),
            ),
          ),
        ],
      ),
      child: Card(
        margin: EdgeInsets.zero,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            children: [
              _CategoryIcon(category: ingredient.category),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(ingredient.name, style: Theme.of(context).textTheme.titleMedium),
                        const SizedBox(width: 8),
                        _StorageBadge(location: ingredient.storageLocation),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${ingredient.quantity}${ingredient.unit} · ${ingredient.category}',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                    const SizedBox(height: 6),
                    _ExpiryBar(ingredient: ingredient, color: _expiryColor),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: _expiryColor.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      ingredient.expiryLabel,
                      style: TextStyle(color: _expiryColor, fontWeight: FontWeight.bold, fontSize: 12),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    AppDateUtils.toDisplay(ingredient.expiryDate),
                    style: Theme.of(context).textTheme.bodyMedium!.copyWith(fontSize: 11),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _edit(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => AddIngredientSheet(ingredient: ingredient),
    );
  }

  void _delete(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('삭제'),
        content: Text('${ingredient.name}을(를) 삭제하시겠어요?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('취소')),
          TextButton(
            onPressed: () {
              context.read<IngredientProvider>().deleteIngredient(ingredient.id!);
              Navigator.pop(ctx);
            },
            child: const Text('삭제', style: TextStyle(color: AppTheme.error)),
          ),
        ],
      ),
    );
  }
}

class _CategoryIcon extends StatelessWidget {
  final String category;
  const _CategoryIcon({required this.category});

  IconData get _icon => switch (category) {
    '채소' => Icons.eco,
    '과일' => Icons.apple,
    '육류' => Icons.set_meal,
    '해산물' => Icons.water,
    '유제품' => Icons.egg,
    '음료' => Icons.local_drink,
    '조미료' => Icons.science,
    _ => Icons.kitchen,
  };

  Color get _color => switch (category) {
    '채소' => AppTheme.secondary,
    '과일' => Colors.orange,
    '육류' => Colors.red,
    '해산물' => Colors.blue,
    '유제품' => Colors.amber,
    '음료' => Colors.cyan,
    '조미료' => Colors.purple,
    _ => AppTheme.primary,
  };

  @override
  Widget build(BuildContext context) {
    return CircleAvatar(
      radius: 24,
      backgroundColor: _color.withOpacity(0.1),
      child: Icon(_icon, color: _color, size: 24),
    );
  }
}

class _StorageBadge extends StatelessWidget {
  final String location;
  const _StorageBadge({required this.location});

  @override
  Widget build(BuildContext context) {
    final color = switch (location) {
      '냉장' => AppTheme.primary,
      '냉동' => Colors.indigo,
      _ => AppTheme.textSecondary,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(location, style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.w500)),
    );
  }
}

class _ExpiryBar extends StatelessWidget {
  final Ingredient ingredient;
  final Color color;
  const _ExpiryBar({required this.ingredient, required this.color});

  @override
  Widget build(BuildContext context) {
    final days = ingredient.daysLeft.clamp(0, 30);
    final ratio = days / 30;
    return ClipRRect(
      borderRadius: BorderRadius.circular(4),
      child: LinearProgressIndicator(
        value: ratio.toDouble(),
        backgroundColor: AppTheme.divider,
        valueColor: AlwaysStoppedAnimation(color),
        minHeight: 4,
      ),
    );
  }
}
