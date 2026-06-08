import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/app_theme.dart';
import '../../../data/models/recipe.dart';
import '../../../data/repositories/ingredient_provider.dart';
import '../../../data/services/recipe_service.dart';

class RecipesScreen extends StatefulWidget {
  const RecipesScreen({super.key});

  @override
  State<RecipesScreen> createState() => _RecipesScreenState();
}

class _RecipesScreenState extends State<RecipesScreen> {
  final RecipeService _recipeService = RecipeService();
  List<Recipe> _recipes = [];
  bool _isLoading = false;
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadRecipes());
  }

  Future<void> _loadRecipes() async {
    setState(() => _isLoading = true);
    final provider = context.read<IngredientProvider>();
    await provider.loadIngredients();
    final names = provider.ingredientNames;
    final recipes = await _recipeService.getRecipesByIngredients(names);
    setState(() {
      _recipes = recipes;
      _isLoading = false;
      _loaded = true;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('레시피 추천'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadRecipes,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : !_loaded || _recipes.isEmpty
              ? _EmptyRecipes(onRefresh: _loadRecipes)
              : RefreshIndicator(
                  onRefresh: _loadRecipes,
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      _HeaderBanner(),
                      const SizedBox(height: 16),
                      Text(
                        '${_recipes.length}개의 레시피를 찾았어요',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 12),
                      ..._recipes.map((r) => Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: _RecipeCard(recipe: r),
                          )),
                    ],
                  ),
                ),
    );
  }
}

class _HeaderBanner extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [AppTheme.primary, AppTheme.secondary],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          const Icon(Icons.restaurant_menu, color: Colors.white, size: 36),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '오늘 뭐 먹을까요?',
                  style: Theme.of(context).textTheme.titleLarge!.copyWith(color: Colors.white),
                ),
                Text(
                  '냉장고 속 재료로 만들 수 있는 요리',
                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(color: Colors.white70),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RecipeCard extends StatelessWidget {
  final Recipe recipe;
  const _RecipeCard({required this.recipe});

  @override
  Widget build(BuildContext context) {
    final matchPct = (recipe.matchScore * 100).toInt();
    return Card(
      child: InkWell(
        onTap: () => _showDetail(context),
        borderRadius: BorderRadius.circular(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (recipe.imageUrl != null)
              ClipRRect(
                borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
                child: CachedNetworkImage(
                  imageUrl: recipe.imageUrl!,
                  height: 160,
                  width: double.infinity,
                  fit: BoxFit.cover,
                  placeholder: (_, __) => Container(
                    height: 160,
                    color: AppTheme.divider,
                    child: const Center(child: CircularProgressIndicator()),
                  ),
                  errorWidget: (_, __, ___) => Container(
                    height: 80,
                    color: AppTheme.divider,
                    child: const Icon(Icons.restaurant, size: 36, color: AppTheme.textSecondary),
                  ),
                ),
              )
            else
              Container(
                height: 80,
                width: double.infinity,
                decoration: const BoxDecoration(
                  color: AppTheme.divider,
                  borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
                ),
                child: const Icon(Icons.restaurant, size: 36, color: AppTheme.textSecondary),
              ),
            Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(recipe.title, style: Theme.of(context).textTheme.titleMedium),
                      ),
                      _MatchBadge(percent: matchPct),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      const Icon(Icons.timer_outlined, size: 16, color: AppTheme.textSecondary),
                      const SizedBox(width: 4),
                      Text('${recipe.readyInMinutes}분', style: Theme.of(context).textTheme.bodyMedium),
                      const SizedBox(width: 16),
                      const Icon(Icons.people_outline, size: 16, color: AppTheme.textSecondary),
                      const SizedBox(width: 4),
                      Text('${recipe.servings}인분', style: Theme.of(context).textTheme.bodyMedium),
                    ],
                  ),
                  if (recipe.usedIngredients.isNotEmpty) ...[
                    const SizedBox(height: 10),
                    Wrap(
                      spacing: 6,
                      runSpacing: 4,
                      children: recipe.usedIngredients.map((ing) => Chip(
                        label: Text(ing, style: const TextStyle(fontSize: 11)),
                        backgroundColor: AppTheme.secondary.withOpacity(0.1),
                        padding: EdgeInsets.zero,
                        materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                        visualDensity: VisualDensity.compact,
                        side: BorderSide.none,
                      )).toList(),
                    ),
                  ],
                  if (recipe.missedIngredients.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      '부족: ${recipe.missedIngredients.join(', ')}',
                      style: Theme.of(context).textTheme.bodyMedium!.copyWith(color: AppTheme.warning),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showDetail(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _RecipeDetailSheet(recipe: recipe),
    );
  }
}

class _MatchBadge extends StatelessWidget {
  final int percent;
  const _MatchBadge({required this.percent});

  @override
  Widget build(BuildContext context) {
    final color = percent >= 80 ? AppTheme.secondary : percent >= 50 ? AppTheme.warning : AppTheme.error;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        '$percent% 매칭',
        style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 12),
      ),
    );
  }
}

class _RecipeDetailSheet extends StatefulWidget {
  final Recipe recipe;
  const _RecipeDetailSheet({required this.recipe});

  @override
  State<_RecipeDetailSheet> createState() => _RecipeDetailSheetState();
}

class _RecipeDetailSheetState extends State<_RecipeDetailSheet> {
  final RecipeService _service = RecipeService();
  late Recipe _recipe;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _recipe = widget.recipe;
    _loadDetail();
  }

  Future<void> _loadDetail() async {
    final detailed = await _service.getRecipeDetail(widget.recipe);
    if (mounted) {
      setState(() {
        _recipe = detailed;
        _loading = false;
      });
    }
  }

  // HTML 태그 제거 (Spoonacular summary는 HTML 포함)
  String _stripHtml(String s) =>
      s.replaceAll(RegExp(r'<[^>]*>'), '').replaceAll('&amp;', '&');

  @override
  Widget build(BuildContext context) {
    final recipe = _recipe;
    final mq = MediaQuery.of(context);
    final maxHeight = mq.size.height * 0.9;
    final bottomSafe = mq.viewPadding.bottom;
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
            const SizedBox(height: 12),
            Center(
              child: Container(
                width: 40, height: 4,
                decoration: BoxDecoration(color: AppTheme.divider, borderRadius: BorderRadius.circular(2)),
              ),
            ),
            const SizedBox(height: 12),
            Flexible(
              child: SingleChildScrollView(
                padding: EdgeInsets.fromLTRB(20, 0, 20, 24 + bottomSafe),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (recipe.imageUrl != null)
                      ClipRRect(
                        borderRadius: BorderRadius.circular(16),
                        child: CachedNetworkImage(
                          imageUrl: recipe.imageUrl!,
                          height: 180,
                          width: double.infinity,
                          fit: BoxFit.cover,
                          placeholder: (_, __) => Container(
                            height: 180, color: AppTheme.divider,
                            child: const Center(child: CircularProgressIndicator()),
                          ),
                          errorWidget: (_, __, ___) => Container(
                            height: 100, color: AppTheme.divider,
                            child: const Icon(Icons.restaurant, size: 40, color: AppTheme.textSecondary),
                          ),
                        ),
                      ),
                    const SizedBox(height: 16),
                    Text(recipe.title, style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        const Icon(Icons.timer_outlined, size: 16, color: AppTheme.textSecondary),
                        const SizedBox(width: 4),
                        Text('${recipe.readyInMinutes}분'),
                        const SizedBox(width: 16),
                        const Icon(Icons.people_outline, size: 16, color: AppTheme.textSecondary),
                        const SizedBox(width: 4),
                        Text('${recipe.servings}인분'),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Text('보유 재료', style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 4,
                      children: recipe.usedIngredients.map((i) => Chip(
                        label: Text(i),
                        backgroundColor: AppTheme.secondary.withOpacity(0.1),
                        side: BorderSide.none,
                      )).toList(),
                    ),
                    if (recipe.missedIngredients.isNotEmpty) ...[
                      const SizedBox(height: 12),
                      Text('필요 재료', style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 4,
                        children: recipe.missedIngredients.map((i) => Chip(
                          label: Text(i),
                          backgroundColor: AppTheme.warning.withOpacity(0.1),
                          side: BorderSide.none,
                        )).toList(),
                      ),
                    ],
                    const SizedBox(height: 20),
                    Text('조리 방법', style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 8),
                    if (_loading)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 20),
                        child: Center(child: CircularProgressIndicator()),
                      )
                    else if (recipe.instructions.isEmpty)
                      Text('조리 방법 정보가 없습니다.',
                          style: Theme.of(context).textTheme.bodyMedium)
                    else
                      ...recipe.instructions.asMap().entries.map((e) => Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Container(
                              width: 26, height: 26,
                              decoration: const BoxDecoration(
                                color: AppTheme.primary,
                                shape: BoxShape.circle,
                              ),
                              alignment: Alignment.center,
                              child: Text('${e.key + 1}',
                                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Text(_stripHtml(e.value),
                                  style: Theme.of(context).textTheme.bodyLarge),
                            ),
                          ],
                        ),
                      )),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyRecipes extends StatelessWidget {
  final VoidCallback onRefresh;
  const _EmptyRecipes({required this.onRefresh});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(40),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.menu_book_outlined, size: 64, color: AppTheme.textSecondary.withOpacity(0.4)),
            const SizedBox(height: 16),
            Text('레시피를 찾을 수 없어요', style: Theme.of(context).textTheme.titleMedium!.copyWith(color: AppTheme.textSecondary)),
            const SizedBox(height: 8),
            Text(
              '냉장고에 식재료를 먼저 추가해보세요',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: onRefresh,
              icon: const Icon(Icons.refresh),
              label: const Text('다시 검색'),
            ),
          ],
        ),
      ),
    );
  }
}
