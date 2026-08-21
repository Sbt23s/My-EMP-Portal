import 'dart:io';

import 'package:dio/dio.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../providers/app_providers.dart';

/// Raise a support ticket. Pops `true` when one was actually created.
///
/// Enhanced to match the web ticket form with category, assigned-to,
/// module, priority, and file attachments.
class RaiseTicketSheet extends ConsumerStatefulWidget {
  const RaiseTicketSheet({super.key});

  @override
  ConsumerState<RaiseTicketSheet> createState() => _RaiseTicketSheetState();
}

class _RaiseTicketSheetState extends ConsumerState<RaiseTicketSheet> {
  final _formKey = GlobalKey<FormState>();
  final _subject = TextEditingController();
  final _description = TextEditingController();

  // Matching what the portal offers, so a ticket raised on the phone lands in
  // the same queues as one raised on the web.
  static const _types = ['IT', 'HR', 'FACILITIES', 'PAYROLL', 'OTHER'];
  static const _categories = [
    'Hardware',
    'Software',
    'Network',
    'Access / Login',
    'Facility',
    'Other',
  ];
  static const _priorities = [
    'LOW',
    'MEDIUM',
    'HIGH',
    'CRITICAL',
  ];
  static const _modules = [
    'Attendance',
    'Leave',
    'Payroll',
    'Tasks',
    'Work Reports',
    'Claims',
    'Assets',
    'Chat',
    'Other',
  ];

  String _type = 'IT';
  String _category = 'Software';
  String _priority = 'MEDIUM';
  String? _module;
  bool _busy = false;

  // File attachments
  final List<String> _attachmentPaths = [];
  bool _uploading = false;

  @override
  void dispose() {
    _subject.dispose();
    _description.dispose();
    super.dispose();
  }

  Future<void> _attach() async {
    final choice = await showModalBottomSheet<int>(
      context: context,
      builder: (sheet) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('Photos'),
              subtitle: const Text('From your gallery'),
              onTap: () => Navigator.of(sheet).pop(0),
            ),
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined),
              title: const Text('Camera'),
              subtitle: const Text('Take a photo now'),
              onTap: () => Navigator.of(sheet).pop(1),
            ),
            ListTile(
              leading: const Icon(Icons.insert_drive_file_outlined),
              title: const Text('Document'),
              subtitle: const Text('PDF, DOC, anything stored'),
              onTap: () => Navigator.of(sheet).pop(2),
            ),
          ],
        ),
      ),
    );
    if (choice == null || !mounted) return;

    setState(() => _uploading = true);
    try {
      final api = ref.read(apiClientProvider);
      final picker = ImagePicker();
      List<File> files = [];

      switch (choice) {
        case 0:
          final imgs = await picker.pickMultiImage();
          files = imgs.map((x) => File(x.path)).toList();
        case 1:
          final img = await picker.pickImage(source: ImageSource.camera);
          if (img != null) files = [File(img.path)];
        case 2:
          final res =
              await FilePicker.platform.pickFiles(allowMultiple: true);
          if (res != null) {
            files = res.paths.whereType<String>().map(File.new).toList();
          }
      }

      if (files.isEmpty) return;

      // Upload each file to the ticket upload endpoint
      for (final file in files) {
        final form = FormData.fromMap({
          'file': await MultipartFile.fromFile(file.path,
              filename: file.uri.pathSegments.last),
        });
        final data = await api.upload('/tickets/upload', form);
        if (data is Map<String, dynamic>) {
          final path = data['path']?.toString();
          if (path != null && path.isNotEmpty) {
            _attachmentPaths.add(path);
          }
        }
      }

      if (mounted) {
        setState(() {});
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${files.length} file(s) attached')),
        );
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('$e'),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  void _removeAttachment(int index) {
    setState(() => _attachmentPaths.removeAt(index));
  }

  Future<void> _submit() async {
    if (_busy) return; // one ticket per tap
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() => _busy = true);
    try {
      // Build description with module info like the web does
      final descParts = <String>[];
      if (_module != null && _module!.isNotEmpty) {
        descParts.add('Affected module: $_module');
      }
      descParts.add(_description.text.trim());
      final fullDescription = descParts.where((s) => s.isNotEmpty).join('\n\n');

      await ref.read(workRepositoryProvider).raiseTicket(
            subject: _subject.text,
            description: fullDescription,
            type: _type,
            priority: _priority,
            category: _category,
            attachments: _attachmentPaths.isNotEmpty
                ? _attachmentPaths.join(',')
                : null,
          );
      if (!mounted) return;
      Navigator.pop(context, true);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Ticket raised — the HR team has been notified')),
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
                'Raise a ticket',
                style: theme.textTheme.titleLarge
                    ?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 20),

              // Subject
              TextFormField(
                controller: _subject,
                enabled: !_busy,
                textCapitalization: TextCapitalization.sentences,
                maxLength: 120,
                decoration: const InputDecoration(
                  labelText: 'Short summary *',
                  hintText: 'Laptop will not start',
                ),
                validator: (v) => (v == null || v.trim().length < 3)
                    ? 'Give it a short subject'
                    : null,
              ),
              const SizedBox(height: 10),

              // Type + Priority row
              Row(
                children: [
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      value: _type,
                      decoration: const InputDecoration(labelText: 'Type *'),
                      items: [
                        for (final t in _types)
                          DropdownMenuItem(value: t, child: Text(t)),
                      ],
                      onChanged: _busy
                          ? null
                          : (v) => setState(() => _type = v ?? 'IT'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      value: _priority,
                      decoration: const InputDecoration(labelText: 'Priority *'),
                      items: [
                        for (final p in _priorities)
                          DropdownMenuItem(
                            value: p,
                            child: Text(p),
                          ),
                      ],
                      onChanged: _busy
                          ? null
                          : (v) => setState(() => _priority = v ?? 'MEDIUM'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),

              // Category
              DropdownButtonFormField<String>(
                value: _category,
                decoration: const InputDecoration(labelText: 'Category *'),
                items: [
                  for (final c in _categories)
                    DropdownMenuItem(value: c, child: Text(c)),
                ],
                onChanged: _busy
                    ? null
                    : (v) => setState(() => _category = v ?? 'Software'),
              ),
              const SizedBox(height: 10),

              // Affected module
              DropdownButtonFormField<String>(
                value: _module,
                decoration: const InputDecoration(
                  labelText: 'Affected module (optional)',
                ),
                items: [
                  const DropdownMenuItem(
                    value: null,
                    child: Text('None'),
                  ),
                  for (final m in _modules)
                    DropdownMenuItem(value: m, child: Text(m)),
                ],
                onChanged: _busy ? null : (v) => setState(() => _module = v),
              ),
              const SizedBox(height: 10),

              // Description
              TextFormField(
                controller: _description,
                enabled: !_busy,
                maxLines: 4,
                maxLength: 2000,
                textCapitalization: TextCapitalization.sentences,
                decoration: const InputDecoration(
                  labelText: 'Description *',
                  hintText:
                      'What you were doing, what happened, what you expected.',
                  alignLabelWithHint: true,
                ),
                validator: (v) => (v == null || v.trim().length < 10)
                    ? 'A sentence or two helps whoever picks this up'
                    : null,
              ),
              const SizedBox(height: 14),

              // File attachments section
              Row(
                children: [
                  Icon(Icons.attach_file,
                      size: 18, color: theme.colorScheme.onSurfaceVariant),
                  const SizedBox(width: 6),
                  Text(
                    'Attachments',
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const Spacer(),
                  if (_uploading)
                    const SizedBox(
                      width: 18,
                      height: 18,
                      child:
                          CircularProgressIndicator(strokeWidth: 2),
                    )
                  else
                    IconButton(
                      onPressed: _busy ? null : _attach,
                      icon: const Icon(Icons.add_circle_outline, size: 22),
                      tooltip: 'Add attachment',
                    ),
                ],
              ),

              if (_attachmentPaths.isNotEmpty) ...[
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (var i = 0; i < _attachmentPaths.length; i++)
                      Chip(
                        avatar: const Icon(Icons.description, size: 16),
                        label: Text(
                          _attachmentPaths[i].split('/').last,
                          overflow: TextOverflow.ellipsis,
                        ),
                        deleteIcon: const Icon(Icons.close, size: 16),
                        onDeleted: () => _removeAttachment(i),
                      ),
                  ],
                ),
              ],
              const SizedBox(height: 8),

              // Hint text like the web
              Text(
                'Max 10MB each. PDF, DOC, DOCX, PNG, JPG, GIF.',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 16),

              FilledButton(
                onPressed: (_busy || _uploading) ? null : _submit,
                child: _busy
                    ? const SizedBox(
                        height: 22,
                        width: 22,
                        child: CircularProgressIndicator(strokeWidth: 2.4),
                      )
                    : const Text('Raise ticket'),
              ),
              const SizedBox(height: 12),
            ],
          ),
        ),
      ),
    );
  }
}
