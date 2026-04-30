import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/utils/date_utils.dart';
import '../../../data/repositories/ingredient_provider.dart';
import '../../refrigerator/screens/refrigerator_screen.dart';
import '../../camera/screens/camera_screen.dart';
import '../../recipes/screens/recipes_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _currentIndex = 0;

  final List<Widget> _screens = const [
    _DashboardTab(),
    RefrigeratorScreen(),
    CameraScreen(),
    RecipesScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (index) => setState(() => _currentIndex = index),
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home_outlined), activeIcon: Icon(Icons.home), label: '홈'),
          BottomNavigationBarItem(icon: Icon(Icons.kitchen_outlined), activeIcon: Icon(Icons.kitchen), label: '냉장고'),
          BottomNavigationBarItem(icon: Icon(Icons.camera_alt_outlined), activeIcon: Icon(Icons.camera_alt), label: '인식'),
          BottomNavigationBarItem(icon: Icon(Icons.menu_book_outlined), activeIcon: Icon(Icons.menu_book), label: '레시피'),
        ],
      ),
    );
  }
}

class _DashboardTab extends StatefulWidget {
  const _DashboardTab();

  @override
  State<_DashboardTab> createState() => _DashboardTabState();
}

class _DashboardTabState extends State<_DashboardTab> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<IngredientProvider>().loadIngredients();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      body: SafeArea(
        child: Consumer<IngredientProvider>(
          builder: (context, provider, _) {
            final expiring = provider.expiringIngredients;
            final all = provider.ingredients;

            return RefreshIndicator(
              onRefresh: provider.loadIngredients,
              child: CustomScrollView(
                slivers: [
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(20, 24, 20, 0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('안녕하세요!', style: Theme.of(context).textTheme.bodyMedium),
                          const SizedBox(height: 4),
                          Text('냉장고 현황', style: Theme.of(context).textTheme.displayLarge),
                          const SizedBox(height: 24),
                          _SummaryCards(total: all.length, expiring: expiring.length),
                          const SizedBox(height: 24),
                        ],
                      ),
                    ),
                  ),
                  if (expiring.isNotEmpty) ...[
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 20),
                        child: Row(
                          children: [
                            const Icon(Icons.warning_amber_rounded, color: AppTheme.warning, size: 20),
                            const SizedBox(width: 8),
                            Text('유통기한 임박', style: Theme.of(context).textTheme.titleMedium),
                          ],
                        ),
                      ),
                    ),
                    const SliverToBoxAdapter(child: SizedBox(height: 12)),
                    SliverList(
                      delegate: SliverChildBuilderDelegate(
                        (context, index) => Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
                          child: _ExpiringCard(ingredient: expiring[index]),
                        ),
                        childCount: expiring.length > 5 ? 5 : expiring.length,
                      ),
                    ),
                    const SliverToBoxAdapter(child: SizedBox(height: 24)),
                  ],
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 20),
                      child: Text('최근 추가 식재료', style: Theme.of(context).textTheme.titleMedium),
                    ),
                  ),
                  const SliverToBoxAdapter(child: SizedBox(height: 12)),
                  if (provider.isLoading)
                    const SliverToBoxAdapter(
                      child: Center(child: CircularProgressIndicator()),
                    )
                  else if (all.isEmpty)
                    SliverToBoxAdapter(
                      child: _EmptyState(),
                    )
                  else
                    SliverList(
                      delegate: SliverChildBuilderDelegate(
                        (context, index) {
                          final sorted = [...all]
                            ..sort((a, b) => b.addedDate.compareTo(a.addedDate));
                          final item = sorted[index];
                          return Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
                            child: _IngredientListTile(ingredient: item),
                          );
                        },
                        childCount: all.length > 10 ? 10 : all.length,
                      ),
                    ),
                  const SliverToBoxAdapter(child: SizedBox(height: 24)),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

class _SummaryCards extends StatelessWidget {
  final int total;
  final int expiring;

  const _SummaryCards({required this.total, required this.expiring});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _StatCard(
            icon: Icons.inventory_2_outlined,
            label: '전체 식재료',
            value: '$total개',
            color: AppTheme.primary,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _StatCard(
            icon: Icons.timer_outlined,
            label: '유통기한 임박',
            value: '$expiring개',
            color: expiring > 0 ? AppTheme.warning : AppTheme.secondary,
          ),
        ),
      ],
    );
  }
}

class _StatCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color color;

  const _StatCard({
    required this.icon,
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: color, size: 28),
            const SizedBox(height: 12),
            Text(label, style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 4),
            Text(value, style: Theme.of(context).textTheme.titleLarge!.copyWith(color: color)),
          ],
        ),
      ),
    );
  }
}

class _ExpiringCard extends StatelessWidget {
  final ingredient;
  const _ExpiringCard({required this.ingredient});

  @override
  Widget build(BuildContext context) {
    final status = ingredient.expiryStatus;
    final color = status == ExpiryStatus.expired
        ? AppTheme.error
        : status == ExpiryStatus.danger
            ? AppTheme.error
            : AppTheme.warning;

    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: color.withOpacity(0.1),
          child: Icon(Icons.kitchen, color: color, size: 20),
        ),
        title: Text(ingredient.name, style: Theme.of(context).textTheme.titleMedium),
        subtitle: Text('${ingredient.storageLocation} · ${ingredient.category}'),
        trailing: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
          decoration: BoxDecoration(
            color: color.withOpacity(0.1),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Text(
            ingredient.expiryLabel,
            style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 13),
          ),
        ),
      ),
    );
  }
}

class _IngredientListTile extends StatelessWidget {
  final ingredient;
  const _IngredientListTile({required this.ingredient});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: AppTheme.primary.withOpacity(0.1),
          child: const Icon(Icons.eco, color: AppTheme.primary, size: 20),
        ),
        title: Text(ingredient.name, style: Theme.of(context).textTheme.titleMedium),
        subtitle: Text('${ingredient.quantity}${ingredient.unit} · ${ingredient.storageLocation}'),
        trailing: Text(
          ingredient.expiryLabel,
          style: TextStyle(
            color: ingredient.expiryStatus == ExpiryStatus.good
                ? AppTheme.secondary
                : AppTheme.warning,
            fontSize: 12,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(40),
      child: Column(
        children: [
          Icon(Icons.kitchen_outlined, size: 64, color: AppTheme.textSecondary.withOpacity(0.4)),
          const SizedBox(height: 16),
          Text(
            '냉장고가 비어있어요',
            style: Theme.of(context).textTheme.titleMedium!.copyWith(color: AppTheme.textSecondary),
          ),
          const SizedBox(height: 8),
          Text(
            '카메라로 식재료를 인식하거나\n직접 추가해보세요',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}
