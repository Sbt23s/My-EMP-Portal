import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../providers/app_providers.dart';

/// Submit a travel-allowance claim. Pops `true` when one was actually created.
///
/// Enhanced to match the web claim form with proper claim types (Petrol,
/// House Rent, Snacks, Room, Construction Things, Others), travel details
/// (hills/plains km, bus fare), and odometer readings.
class SubmitClaimSheet extends ConsumerStatefulWidget {
  const SubmitClaimSheet({super.key});

  @override
  ConsumerState<SubmitClaimSheet> createState() => _SubmitClaimSheetState();
}

class _SubmitClaimSheetState extends ConsumerState<SubmitClaimSheet> {
  final _formKey = GlobalKey<FormState>();
  final _location = TextEditingController();
  final _startingKm = TextEditingController();
  final _endingKm = TextEditingController();
  final _hillsKm = TextEditingController();
  final _plainsKm = TextEditingController();
  final _busFare = TextEditingController();
  final _others = TextEditingController();
  final _itemName = TextEditingController();
  final _amount = TextEditingController();
  final _remarks = TextEditingController();

  /// The claim types the web offers, with hints about what each one needs.
  static const _categories = [
    'Petrol',
    'House Rent',
    'Snacks',
    'Room',
    'Construction Things',
    'Others',
  ];

  String _category = 'Petrol';
  DateTime _date = DateTime.now();
  bool _busy = false;

  bool get _isTravel => _category == 'Petrol';

  num _num(String v) => num.tryParse(v.trim()) ?? 0;

  int get _totalKm => (_num(_endingKm.text) - _num(_startingKm.text)).toInt().clamp(0, 99999);
  num get _travelAllowance {
    // Simple placeholder rates; the real rates come from /settings
    const hillsRate = 12.0;
    const plainsRate = 8.0;
    return _num(_hillsKm.text) * hillsRate + _num(_plainsKm.text) * plainsRate;
  }
  num get _grossTotal => _isTravel
      ? _travelAllowance + _num(_busFare.text) + _num(_others.text)
      : _num(_amount.text);

  /// Item label depending on claim type
  String get _itemLabel => switch (_category) {
    'House Rent' => 'Rent period',
    'Snacks' => 'Items bought',
    'Room' => 'Hotel / room',
    'Construction Things' => 'Material / item',
    _ => 'What was this for?',
  };

  String? get _itemHint => switch (_category) {
    'House Rent' => 'e.g. July 2026',
    'Snacks' => 'e.g. Tea and biscuits for client meeting',
    _ => null,
  };

  String? get _qtyLabel => switch (_category) {
    'Room' => 'Nights',
    'Construction Things' => 'Quantity',
    _ => null,
  };

  @override
  void dispose() {
    _location.dispose();
    _startingKm.dispose();
    _endingKm.dispose();
    _hillsKm.dispose();
    _plainsKm.dispose();
    _busFare.dispose();
    _others.dispose();
    _itemName.dispose();
    _amount.dispose();
    _remarks.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(now.year - 1, now.month, now.day),
      lastDate: now,
    );
    if (picked != null && mounted) setState(() => _date = picked);
  }

  Future<void> _submit() async {
    if (_busy) return;
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() => _busy = true);
    try {
      // Compose remarks like the web does for non-travel claims
      String? composedRemarks;
      if (!_isTravel) {
        final parts = <String>[];
        if (_itemName.text.trim().isNotEmpty) parts.add(_itemName.text.trim());
        if (_qtyLabel != null && _amount.text.trim().isNotEmpty) {
          parts.add('${_qtyLabel!}: ${_amount.text.trim()}');
        }
        if (_remarks.text.trim().isNotEmpty) parts.add(_remarks.text.trim());
        composedRemarks = parts.join(' · ');
      }

      await ref.read(workRepositoryProvider).submitClaim(
            date: _date,
            location: _location.text,
            category: _category,
            totalKm: _isTravel ? _totalKm : null,
            totalAmount: _isTravel ? _travelAllowance : _num(_amount.text),
            remarks: _isTravel ? _remarks.text : composedRemarks,
            startingKm: _isTravel ? _num(_startingKm.text).toInt() : null,
            endingKm: _isTravel ? _num(_endingKm.text).toInt() : null,
            hillsKm: _isTravel ? _num(_hillsKm.text).toInt() : null,
            plainsKm: _isTravel ? _num(_plainsKm.text).toInt() : null,
            busFare: _isTravel ? _num(_busFare.text) : null,
            others: _isTravel ? _num(_others.text) : null,
          );
      if (!mounted) return;
      Navigator.pop(context, true);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Claim submitted for approval')),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _busy = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(e.toString()),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    }
  }

  String? _required(String? v, {String? hint}) {
    if (v == null || v.trim().isEmpty) return hint ?? 'Required';
    return null;
  }

  String? _posNumber(String? v, {String noun = 'value'}) {
    final text = v?.trim() ?? '';
    if (text.isEmpty) return null; // optional
    final parsed = num.tryParse(text);
    if (parsed == null) return 'Numbers only';
    if (parsed <= 0) return 'Must be more than zero';
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.outlineVariant,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 20),
              Text(
                'Submit a claim',
                style: theme.textTheme.titleLarge
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 20),

              // Date
              InputDecorator(
                decoration: const InputDecoration(labelText: 'Date of expense *'),
                child: InkWell(
                  onTap: _busy ? null : _pickDate,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '${_date.day.toString().padLeft(2, '0')}/'
                        '${_date.month.toString().padLeft(2, '0')}/'
                        '${_date.year}',
                        style: theme.textTheme.bodyLarge,
                      ),
                      const Icon(Icons.calendar_today_outlined, size: 18),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 14),

              // Claim type
              DropdownButtonFormField<String>(
                value: _category,
                decoration: const InputDecoration(labelText: 'Claim type *'),
                items: [
                  for (final c in _categories)
                    DropdownMenuItem(value: c, child: Text(c)),
                ],
                onChanged: _busy
                    ? null
                    : (v) => setState(() => _category = v ?? 'Petrol'),
              ),
              const SizedBox(height: 14),

              // Location
              TextFormField(
                controller: _location,
                enabled: !_busy,
                textCapitalization: TextCapitalization.words,
                maxLength: 120,
                decoration: const InputDecoration(
                  labelText: 'Location / city *',
                  hintText: 'Coimbatore client site',
                ),
                validator: (v) => _required(v, hint: 'Where was this expense?'),
              ),
              const SizedBox(height: 14),

              // Travel section (Petrol only)
              if (_isTravel) ...[
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.surfaceContainerHighest
                        .withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'TRAVEL',
                        style: theme.textTheme.labelSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.5,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: TextFormField(
                              controller: _startingKm,
                              enabled: !_busy,
                              keyboardType: TextInputType.number,
                              decoration: const InputDecoration(
                                labelText: 'Starting KM',
                              ),
                              validator: (v) => _posNumber(v, noun: 'distance'),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: TextFormField(
                              controller: _endingKm,
                              enabled: !_busy,
                              keyboardType: TextInputType.number,
                              decoration: const InputDecoration(
                                labelText: 'Ending KM',
                              ),
                              validator: (v) => _posNumber(v, noun: 'distance'),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Expanded(
                            child: TextFormField(
                              controller: _hillsKm,
                              enabled: !_busy,
                              keyboardType: TextInputType.number,
                              decoration: const InputDecoration(
                                labelText: 'Hills KM',
                                hintText: '0',
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: TextFormField(
                              controller: _plainsKm,
                              enabled: !_busy,
                              keyboardType: TextInputType.number,
                              decoration: const InputDecoration(
                                labelText: 'Plains KM',
                                hintText: '0',
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          Expanded(
                            child: TextFormField(
                              controller: _busFare,
                              enabled: !_busy,
                              keyboardType:
                                  const TextInputType.numberWithOptions(decimal: true),
                              decoration: const InputDecoration(
                                labelText: 'Bus fare',
                                prefixText: '₹ ',
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: TextFormField(
                              controller: _others,
                              enabled: !_busy,
                              keyboardType:
                                  const TextInputType.numberWithOptions(decimal: true),
                              decoration: const InputDecoration(
                                labelText: 'Other expenses',
                                prefixText: '₹ ',
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      // Summary
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 12, vertical: 8),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.primary.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              'Distance: $_totalKm KM',
                              style: theme.textTheme.bodySmall?.copyWith(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            Text(
                              '₹${_travelAllowance.toStringAsFixed(0)}',
                              style: theme.textTheme.titleMedium?.copyWith(
                                fontWeight: FontWeight.w700,
                                color: theme.colorScheme.primary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
              ],

              // Non-travel: item name, quantity, amount
              if (!_isTravel) ...[
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.surfaceContainerHighest
                        .withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '$_category DETAILS',
                        style: theme.textTheme.labelSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.5,
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        controller: _itemName,
                        enabled: !_busy,
                        decoration: InputDecoration(
                          labelText: '$_itemLabel *',
                          hintText: _itemHint,
                        ),
                        validator: (v) => _required(v, hint: _itemHint == null ? 'Required' : null),
                      ),
                      if (_qtyLabel != null) ...[
                        const SizedBox(height: 10),
                        TextFormField(
                          controller: _amount,
                          enabled: !_busy,
                          keyboardType: TextInputType.number,
                          decoration: InputDecoration(
                            labelText: _qtyLabel!,
                          ),
                        ),
                      ],
                      if (_qtyLabel == null) ...[
                        const SizedBox(height: 10),
                        TextFormField(
                          controller: _amount,
                          enabled: !_busy,
                          keyboardType:
                              const TextInputType.numberWithOptions(decimal: true),
                          decoration: const InputDecoration(
                            labelText: 'Amount (₹) *',
                            prefixText: '₹ ',
                          ),
                          validator: (v) => _posNumber(v, noun: 'amount'),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 14),
              ],

              // Total for non-travel
              if (!_isTravel)
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 14, vertical: 10),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('Total claim'),
                      Text(
                        '₹${_grossTotal.toStringAsFixed(0)}',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w700,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: 14),

              // Remarks
              TextFormField(
                controller: _remarks,
                enabled: !_busy,
                maxLines: 3,
                maxLength: 500,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  labelText: 'Purpose / remarks',
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: _busy ? null : _submit,
                child: _busy
                    ? const SizedBox(
                        height: 22,
                        width: 22,
                        child: CircularProgressIndicator(strokeWidth: 2.4),
                      )
                    : const Text('Submit claim'),
              ),
              const SizedBox(height: 12),
            ],
          ),
        ),
      ),
    );
  }
}
