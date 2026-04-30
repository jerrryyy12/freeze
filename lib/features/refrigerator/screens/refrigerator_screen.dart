import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/constants/app_constants.dart';
import '../../../core/theme/app_theme.dart';
import '../../../data/repositories/ingredient_provider.dart';
import '../widgets/add_ingredient_sheet.dart';
import '../widgets/ingredient_card.dart';

class RefrigeratorScreen extends StatefulWidget {
  const RefrigeratorScreen({super.key});

  @override
  State<RefrigeratorScreen> createState() => _RefrigeratorScreenState();
}

class _RefrigeratorScreenState extends State<RefrigeratorScreen> {
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<IngredientProvider>().loadIngredients();
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('내 냉장고'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: () => _showAddSheet(context),
          ),
        ],
      ),
      body: Column(
        children: [
          _SearchBar(controller: _searchController),
          _StorageFilter(),
          _CategoryFilter(),
          Expanded(child: _IngredientList()),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showAddSheet(context),
        icon: const Icon(Icons.add),
        label: const Text('식재료 추가'),
        backgroundColor: AppTheme.primary,
        foregroundColor: Colors.white,
      ),
    );
  }

  void _showAddSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const AddIngredientSheet(),
    );
  }
}

class _SearchBar extends StatelessWidget {
  final TextEditingController controller;
  const _SearchBar({required this.controller});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: TextField(
        controller: controller,
        onChanged: context.read<IngredientProvider>().setSearchQuery,
        decoration: const InputDecoration(
          hintText: '식재료 검색...',
          prefixIcon: Icon(Icons.search, color: AppTheme.textSecondary),
        ),
      ),
    );
  }
}

class _StorageFilter extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final locations = ['전체', ...AppConstants.storageLocations];
    return Consumer<IngredientProvider>(
      builder: (context, provider, _) => SizedBox(
        height: 44,
        child: ListView.separated(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          scrollDirection: Axis.horizontal,
          itemCount: locations.length,
          separatorBuilder: (_, __) => const SizedBox(width: 8),
          itemBuilder: (context, index) {
            final loc = locations[index];
            final selected = provider.selectedStorage == loc;
            return FilterChip(
              label: Text(loc),
              selected: selected,
              onSelected: (_) => provider.setStorage(loc),
              selectedColor: AppTheme.primary.withOpacity(0.15),
              checkmarkColor: AppTheme.primary,
              labelStyle: TextStyle(
                color: selected ? AppTheme.primary : AppTheme.textSecondary,
                fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
              ),
            );
          },
        ),
      ),
    );
  }
}

class _CategoryFilter extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Consumer<IngredientProvider>(
      builder: (context, provider, _) => SizedBox(
        height: 44,
        child: ListView.separated(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          scrollDirection: Axis.horizontal,
          itemCount: AppConstants.categories.length,
          separatorBuilder: (_, __) => const SizedBox(width: 8),
          itemBuilder: (context, index) {
            final cat = AppConstants.categories[index];
            final selected = provider.selectedCategory == cat;
            return FilterChip(
              label: Text(cat),
              selected: selected,
              onSelected: (_) => provider.setCategory(cat),
              selectedColor: AppTheme.secondary.withOpacity(0.15),
              checkmarkColor: AppTheme.secondary,
              labelStyle: TextStyle(
                color: selected ? AppTheme.secondary : AppTheme.textSecondary,
                fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
              ),
            );
          },
        ),
      ),
    );
  }
}

class _IngredientList extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Consumer<IngredientProvider>(
      builder: (context, provider, _) {
        if (provider.isLoading) {
          return const Center(child: CircularProgressIndicator());
        }
        final items = provider.ingredients;
        if (items.isEmpty) {
          return Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.search_off, size: 56, color: AppTheme.textSecondary.withOpacity(0.4)),
                const SizedBox(height: 12),
                Text('식재료가 없습니다', style: Theme.of(context).textTheme.titleMedium!.copyWith(color: AppTheme.textSecondary)),
              ],
            ),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
          itemCount: items.length,
          separatorBuilder: (_, __) => const SizedBox(height: 8),
          itemBuilder: (context, index) => IngredientCard(ingredient: items[index]),
        );
      },
    );
  }
}
