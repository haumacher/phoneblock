import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:fritz_tr064/fritz_tr064.dart' show AuthMethod, AuthMethodButton, AuthMethodDtmf;
import 'package:phoneblock_mobile/fritzbox/fritzbox_service.dart';
import 'package:phoneblock_mobile/l10n/app_localizations.dart';

/// Status of a single setup step.
enum _StepStatus { pending, inProgress, completed, failed }

/// Tracks the state of a single setup step.
class _SetupStepInfo {
  final AnswerbotSetupStep step;
  final String label;
  _StepStatus status;
  String? detail;
  String? errorMessage;

  _SetupStepInfo({required this.step, required this.label, this.status = _StepStatus.pending});
}

/// Dedicated screen that shows step-by-step progress for answerbot setup.
///
/// Returns `true` via [Navigator.pop] on success, `false` on failure.
class FritzBoxAnswerbotSetupScreen extends StatefulWidget {
  const FritzBoxAnswerbotSetupScreen({super.key});

  @override
  State<FritzBoxAnswerbotSetupScreen> createState() =>
      _FritzBoxAnswerbotSetupScreenState();
}

class _FritzBoxAnswerbotSetupScreenState
    extends State<FritzBoxAnswerbotSetupScreen> {
  final List<_SetupStepInfo> _steps = [];
  bool _finished = false;
  bool _succeeded = false;

  bool get _isWaitingForSecondFactor =>
      _steps.isNotEmpty &&
      _steps.last.status == _StepStatus.inProgress &&
      _steps.last.step == AnswerbotSetupStep.confirmingSecondFactor;

  @override
  void initState() {
    super.initState();
    // Defer to after the first frame so setState works correctly
    // (setupAnswerBot calls onProgress synchronously before its first await).
    SchedulerBinding.instance.addPostFrameCallback((_) => _runSetup());
  }

  Future<void> _runSetup() async {
    try {
      await FritzBoxService.instance.setupAnswerBot(
        onProgress: (step) {
          if (!mounted) return;
          setState(() {
            _onProgress(step);
          });
        },
        onSecondFactorMethods: (methods) {
          if (!mounted) return;
          setState(() {
            _onSecondFactorMethods(methods);
          });
        },
      );

      if (mounted) {
        setState(() {
          // Mark last in-progress step as completed.
          _markCurrentCompleted();
          _succeeded = true;
          _finished = true;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          // Mark current in-progress step as failed.
          for (final step in _steps) {
            if (step.status == _StepStatus.inProgress) {
              step.status = _StepStatus.failed;
              step.errorMessage = e.toString();
              break;
            }
          }
          _finished = true;
        });
      }
    }
  }

  void _onProgress(AnswerbotSetupStep step) {
    if (step == AnswerbotSetupStep.complete) return;

    // Mark previous in-progress step as completed.
    _markCurrentCompleted();

    // Add new step as in-progress.
    final l10n = AppLocalizations.of(context)!;
    _steps.add(_SetupStepInfo(
      step: step,
      label: _stepLabel(l10n, step),
      status: _StepStatus.inProgress,
    ));
  }

  void _onSecondFactorMethods(List<AuthMethod> methods) {
    // Find the 2FA step and set its detail to describe the available methods.
    for (final step in _steps) {
      if (step.step == AnswerbotSetupStep.confirmingSecondFactor) {
        step.detail = _formatAuthMethods(methods);
        break;
      }
    }
  }

  String _formatAuthMethods(List<AuthMethod> methods) {
    final l10n = AppLocalizations.of(context)!;
    final parts = <String>[];
    for (final method in methods) {
      if (method is AuthMethodButton) {
        parts.add(l10n.fritzboxSecondFactorButton);
      } else if (method is AuthMethodDtmf) {
        parts.add(l10n.fritzboxSecondFactorDtmf(method.sequence));
      }
    }
    return parts.join('\n');
  }

  void _markCurrentCompleted() {
    for (final step in _steps) {
      if (step.status == _StepStatus.inProgress) {
        step.status = _StepStatus.completed;
      }
    }
  }

  String _stepLabel(AppLocalizations l10n, AnswerbotSetupStep step) {
    switch (step) {
      case AnswerbotSetupStep.creatingBot:
        return l10n.fritzboxAnswerbotStepCreating;
      case AnswerbotSetupStep.detectingAccess:
        return l10n.fritzboxAnswerbotStepDetecting;
      case AnswerbotSetupStep.configuringDynDns:
        return l10n.fritzboxAnswerbotStepDynDns;
      case AnswerbotSetupStep.waitingForDynDns:
        return l10n.fritzboxAnswerbotStepWaitingDynDns;
      case AnswerbotSetupStep.registeringSipDevice:
        return l10n.fritzboxAnswerbotStepSip;
      case AnswerbotSetupStep.confirmingSecondFactor:
        return l10n.fritzboxAnswerbotStepSecondFactor;
      case AnswerbotSetupStep.enablingInternetAccess:
        return l10n.fritzboxAnswerbotStepInternetAccess;
      case AnswerbotSetupStep.enablingBot:
        return l10n.fritzboxAnswerbotStepEnabling;
      case AnswerbotSetupStep.waitingForRegistration:
        return l10n.fritzboxAnswerbotStepWaiting;
      case AnswerbotSetupStep.complete:
        return '';
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;

    return PopScope(
      canPop: _finished,
      child: Scaffold(
        appBar: AppBar(
          title: Text(l10n.fritzboxAnswerbotSetupTitle),
          automaticallyImplyLeading: _finished,
        ),
        body: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            children: [
              Expanded(
                child: ListView.builder(
                  itemCount: _steps.length,
                  itemBuilder: (context, index) {
                    final step = _steps[index];
                    return ListTile(
                      leading: _buildStepIcon(step),
                      title: Text(step.label),
                      subtitle: step.errorMessage != null
                          ? Text(
                              l10n.fritzboxAnswerbotSetupErrorDetail(
                                  step.errorMessage!),
                              style: const TextStyle(color: Colors.red),
                            )
                          : step.detail != null
                              ? Text(step.detail!)
                              : null,
                    );
                  },
                ),
              ),
              if (_isWaitingForSecondFactor && !_finished)
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton(
                    onPressed: () {
                      FritzBoxService.instance.cancelSecondFactor();
                    },
                    child: Text(l10n.cancel),
                  ),
                ),
              if (_finished) ...[
                _buildResultCard(l10n),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    onPressed: () => Navigator.pop(context, _succeeded),
                    child: Text(_succeeded ? l10n.done : l10n.cancel),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStepIcon(_SetupStepInfo step) {
    switch (step.status) {
      case _StepStatus.pending:
        return const Icon(Icons.radio_button_unchecked, color: Colors.grey);
      case _StepStatus.inProgress:
        return const SizedBox(
          width: 24,
          height: 24,
          child: CircularProgressIndicator(strokeWidth: 2),
        );
      case _StepStatus.completed:
        return const Icon(Icons.check_circle, color: Colors.green);
      case _StepStatus.failed:
        return const Icon(Icons.error, color: Colors.red);
    }
  }

  Widget _buildResultCard(AppLocalizations l10n) {
    return Card(
      color: _succeeded ? Colors.green.shade50 : Colors.red.shade50,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Row(
          children: [
            Icon(
              _succeeded ? Icons.check_circle : Icons.error,
              color: _succeeded ? Colors.green : Colors.red,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                _succeeded
                    ? l10n.fritzboxAnswerbotSetupSuccess
                    : l10n.fritzboxAnswerbotSetupFailed,
                style: TextStyle(
                  color: _succeeded
                      ? Colors.green.shade900
                      : Colors.red.shade900,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
