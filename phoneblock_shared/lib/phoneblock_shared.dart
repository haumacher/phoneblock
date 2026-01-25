/// PhoneBlock shared components library
///
/// This library contains shared code for PhoneBlock applications,
/// including answerbot screens, API clients, and data models.
library phoneblock_shared;

// Localization
// Note: AppLocalizations is not exported to avoid name conflicts with consuming apps.
// Use the extension method context.answerbotL10n to access answerbot localizations.
// Apps must add answerbotLocalizationsDelegate to their localizationsDelegates list.
export 'src/l10n_extensions.dart';
export 'src/answerbot_localizations_delegate.dart';

// Models
export 'src/models/proto.dart';

// API
export 'src/api/Api.dart';
export 'src/api/auth_provider.dart';
export 'src/api/base_path.dart';
export 'src/api/sendRequest.dart';
export 'src/api/httpAddons.dart';

// Screens
export 'src/screens/AnswerBotList.dart';
export 'src/screens/AnswerBotView.dart';
export 'src/screens/BotSetupForm.dart';
export 'src/screens/CallListView.dart';

// Widgets
export 'src/widgets/ErrorDialog.dart';
export 'src/widgets/InfoField.dart';
export 'src/widgets/TitleRow.dart';
export 'src/widgets/switchIcon.dart';
