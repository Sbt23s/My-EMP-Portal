import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../providers/app_providers.dart';

/// Submit a travel-allowance claim. Pops `true` when one was actually created.
///
/// The fields here are exactly the ones the claims list displays back — date,
/// location, category, distance, amount. Asking for anything the list cannot
/// show would leave the person unable to check what they submitted.
class SubmitClaimSheet extends ConsumerStatefulWidget {
  const SubmitClaimSheet({super.key});

  @override
  ConsumerState<SubmitClaimSheet> createState() => _SubmitClaimSheetState();
}

class _SubmitClaimSheetState extends ConsumerState<SubmitClaimSheet> {
  final _formKey = GlobalKey<FormState>();
  final _location = TextEditingController();
  final _totalKm = TextEditingController();
  final _amount = TextEditingController();
  final _remarks = TextEditingController();

  static const _categories = ['TRAVEL', 'FOOD', 'STAY', 'FUEL', 'OTHER'];

  String _category = 'TRAVEL';
  DateTime _date = DateTime.now();
  bool _busy = false;

  @override
  void dispose() {
    _location.dispose();
    _totalKm.dispose();
    _amount.dispose();
    _remarks.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      // A claim is for a journey already made, so tomorrow is not offerable.
      // A year back covers a genuinely late claim without opening the whole
      // calendar.
      firstDate: DateTime(now.year - 1, now.month, now.day),
      lastDate: now,
    );
    if (picked != null && mounted) setState(() => _date = picked);
  }

  Future<void> _submit() async {
    if (_busy) return; // one claim per tap
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() => _busy = true);
    try {
      await ref
          .read(workRepositoryProvider)
          .submitClaim(
            date: _date,
            location: _location.text,
            category: _category,
            totalKm: int.tryParse(_totalKm.text.trim()),
            totalAmount: num.tryParse(_amount.text.trim()),
            remarks: _remarks.text,
          );
      if (!mounted) return;
      Navigator.pop(context, true);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Claim submitted')));
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

  /// Optional, but must be a sensible positive number when filled in.
  String? _optionalNumber(String? v, {required String noun}) {
    final text = v?.trim() ?? '';
    if (text.isEmpty) return null;
    final parsed = num.tryParse(text);
    if (parsed == null) return 'Numbers only';
    if (parsed <= 0) return 'A $noun of zero is not worth claiming';
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
                style: theme.textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 20),
              InputDecorator(
                decoration: const InputDecoration(labelText: 'Date of travel'),
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
              TextFormField(
                controller: _location,
                enabled: !_busy,
                textCapitalization: TextCapitalization.words,
                maxLength: 120,
                decoration: const InputDecoration(
                  labelText: 'Where to',
                  hintText: 'Coimbatore client site',
                ),
                validator: (v) => (v == null || v.trim().length < 2)
                    ? 'Whoever approves this needs to know where'
                    : null,
              ),
              DropdownButtonFormField<String>(
                initialValue: _category,
                decoration: const InputDecoration(labelText: 'Category'),
                items: [
                  for (final c in _categories)
                    DropdownMenuItem(value: c, child: Text(c)),
                ],
                onChanged: _busy
                    ? null
                    : (v) => setState(() => _category = v ?? 'TRAVEL'),
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: TextFormField(
                      controller: _totalKm,
                      enabled: !_busy,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                        labelText: 'Distance (km)',
                      ),
                      validator: (v) => _optionalNumber(v, noun: 'distance'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextFormField(
                      controller: _amount,
                      enabled: !_busy,
                      keyboardType: const TextInputType.numberWithOptions(
                        decimal: true,
                      ),
                      decoration: const InputDecoration(
                        labelText: 'Amount',
                        prefixText: '₹ ',
                      ),
                      validator: (v) => _optionalNumber(v, noun: 'claim'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              TextFormField(
                controller: _remarks,
                enabled: !_busy,
                maxLines: 3,
                maxLength: 500,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  labelText: 'Remarks (optional)',
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
