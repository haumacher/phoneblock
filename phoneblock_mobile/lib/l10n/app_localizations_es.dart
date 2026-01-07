// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Spanish Castilian (`es`).
class AppLocalizationsEs extends AppLocalizations {
  AppLocalizationsEs([String locale = 'es']) : super(locale);

  @override
  String get appTitle => 'PhoneBlock Móvil';

  @override
  String get settings => 'Ajustes';

  @override
  String get deleteAll => 'Borrar todo';

  @override
  String get noCallsYet => 'Aún no se han filtrado llamadas';

  @override
  String get noCallsDescription =>
      'PhoneBlock filtrará automáticamente las llamadas entrantes y bloqueará las llamadas SPAM.';

  @override
  String get blocked => 'Bloqueado';

  @override
  String get accepted => 'Aceptado';

  @override
  String votes(Object count) {
    return '$count votos';
  }

  @override
  String get viewOnPhoneBlock => 'Mostrar en PhoneBlock';

  @override
  String get confirmDeleteAll => '¿Borrar todas las llamadas filtradas?';

  @override
  String get confirmDeleteAllMessage => 'Esta acción no puede deshacerse.';

  @override
  String get cancel => 'Cancelar';

  @override
  String get delete => 'Borrar';

  @override
  String get settingsTitle => 'Ajustes';

  @override
  String get callScreening => 'Filtrado de llamadas';

  @override
  String get minSpamReports => 'Mensajes SPAM mínimos';

  @override
  String minSpamReportsDescription(Object count) {
    return 'Los números se bloquean a partir de $count mensajes';
  }

  @override
  String get blockNumberRanges => 'Rangos de números de bloque';

  @override
  String get blockNumberRangesDescription =>
      'Bloquear zonas con muchos mensajes SPAM';

  @override
  String get minSpamReportsInRange => 'Mensajes SPAM mínimos en el área de';

  @override
  String minSpamReportsInRangeDescription(Object count) {
    return 'Las zonas se bloquean a partir de $count mensajes';
  }

  @override
  String get about => 'Acerca de';

  @override
  String get version => 'Versión';

  @override
  String get developer => 'Desarrollador';

  @override
  String get developerName => 'Bernhard Haumacher';

  @override
  String get website => 'Página web';

  @override
  String get websiteUrl => 'phoneblock.net';

  @override
  String get sourceCode => 'Código fuente';

  @override
  String get sourceCodeLicense => 'Código abierto (GPL-3.0)';

  @override
  String get aboutDescription =>
      'PhoneBlock es un proyecto de código abierto sin seguimiento y sin publicidad. El servicio se financia mediante donaciones.';

  @override
  String get donate => 'Donaciones';

  @override
  String pendingCallsNotification(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nuevas llamadas filtradas.',
      one: '1 nueva llamada filtrada',
    );
    return '$_temp0';
  }

  @override
  String get tapToOpen => 'Pulse para abrir la aplicación';

  @override
  String get setupWelcome => 'Bienvenido a PhoneBlock Mobile';

  @override
  String get setupPermissionsRequired => 'Autorizaciones necesarias';

  @override
  String get grantPermission => 'Conceder autorización';

  @override
  String get continue_ => 'Más información en';

  @override
  String get finish => 'Terminado';

  @override
  String get loginRequired => 'Registro en PhoneBlock';

  @override
  String get loginToPhoneBlock => 'Registrarse en PhoneBlock';

  @override
  String get verifyingLogin => 'Se comprobará el registro...';

  @override
  String get loginFailed => 'Error de inicio de sesión';

  @override
  String get loginSuccess => 'Inscripción realizada con éxito';

  @override
  String get reportAsLegitimate => 'Denunciar como legítimo';

  @override
  String get reportAsSpam => 'Reportar como SPAM';

  @override
  String get viewOnPhoneBlockMenu => 'Ver en PhoneBlock';

  @override
  String get deleteCall => 'Borrar';

  @override
  String get report => 'Informe';

  @override
  String get notLoggedIn => 'No está registrado. Por favor, identifíquese.';

  @override
  String reportedAsLegitimate(Object phoneNumber) {
    return '$phoneNumber reportado como legítimo';
  }

  @override
  String reportError(Object error) {
    return 'Error al informar: $error';
  }

  @override
  String reportedAsSpam(Object phoneNumber) {
    return '$phoneNumber reportado como SPAM';
  }

  @override
  String get selectSpamCategory => 'Seleccione la categoría SPAM';

  @override
  String get errorDeletingAllCalls => 'Error al borrar todas las llamadas';

  @override
  String get errorDeletingCall => 'Error al cancelar la llamada';

  @override
  String get notLoggedInShort => 'No registrado';

  @override
  String get errorOpeningPhoneBlock => 'Error al abrir PhoneBlock.';

  @override
  String get permissionNotGranted => 'No se ha concedido la autorización.';

  @override
  String get setupTitle => 'PhoneBlock Mobile - Configuración';

  @override
  String get welcome => 'Bienvenido a';

  @override
  String get connectPhoneBlockAccount => 'Conectar cuenta PhoneBlock';

  @override
  String get permissions => 'Autorizaciones';

  @override
  String get allowCallFiltering => 'Permitir el filtrado de llamadas';

  @override
  String get done => 'Terminado';

  @override
  String get setupComplete => 'Instalación finalizada';

  @override
  String get minReportsCount => 'Número mínimo de mensajes';

  @override
  String callsBlockedAfterReports(Object count) {
    return 'Las llamadas se bloquean a partir de $count mensajes';
  }

  @override
  String rangesBlockedAfterReports(Object count) {
    return 'Las zonas se bloquean a partir de $count mensajes';
  }

  @override
  String get welcomeMessage =>
      '¡Bienvenido a PhoneBlock Mobile!\n\nEsta aplicación le ayuda a bloquear las llamadas de spam de forma automática. Necesita una cuenta gratuita en PhoneBlock.net.\n\nConecta tu cuenta PhoneBlock para continuar:';

  @override
  String get connectToPhoneBlock => 'Conéctate con PhoneBlock';

  @override
  String get connectedToPhoneBlock => 'Conectado con PhoneBlock';

  @override
  String get accountConnectedSuccessfully => 'Cuenta conectada correctamente';

  @override
  String get permissionsMessage =>
      'Para bloquear automáticamente las llamadas de spam, PhoneBlock Mobile requiere autorización para comprobar las llamadas entrantes.\n\nEsta autorización es necesaria para que la aplicación funcione:';

  @override
  String get permissionGranted => 'Autorización concedida';

  @override
  String get permissionGrantedSuccessfully =>
      '✓ Autorización concedida con éxito';

  @override
  String get setupCompleteMessage =>
      'Instalación finalizada.\n\nPhoneBlock Mobile ya está listo para bloquear las llamadas de spam. La aplicación examina automáticamente las llamadas entrantes y bloquea los números de spam conocidos en base a la base de datos PhoneBlock.\n\nPulse \"Hecho\" para ir a la vista principal.';

  @override
  String get verifyingLoginTitle => 'Comprobar inicio de sesión';

  @override
  String get loginSuccessMessage => 'Inicio de sesión correcto.';

  @override
  String get redirectingToSetup => 'Reenvío a la instalación...';

  @override
  String tokenVerificationFailed(Object error) {
    return 'Ha fallado la verificación del token: $error';
  }

  @override
  String get backToSetup => 'Volver a las instalaciones';

  @override
  String get tokenBeingVerified => 'La ficha está comprobada...';

  @override
  String get failedToOpenPhoneBlock => 'PhoneBlock no se ha podido abrir.';

  @override
  String get ratingLegitimate => 'Legítimo';

  @override
  String get ratingAdvertising => 'Publicidad';

  @override
  String get ratingSpam => 'SPAM';

  @override
  String get ratingPingCall => 'Llamada ping';

  @override
  String get ratingGamble => 'Concurso';

  @override
  String get ratingFraud => 'Fraude';

  @override
  String get ratingPoll => 'Encuesta';

  @override
  String get noLoginTokenReceived =>
      'No se ha recibido ningún token de inicio de sesión.';

  @override
  String get settingSaved => 'Ajuste guardado';

  @override
  String get errorSaving => 'Error al guardar';

  @override
  String ratePhoneNumber(Object phoneNumber) {
    return 'Tarifa $phoneNumber';
  }

  @override
  String reportsCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count mensajes',
      one: '1 Mensaje',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Mensajes legítimos',
      one: '1 Mensaje legítimo',
    );
    return '$_temp0';
  }

  @override
  String get noReports => 'Sin mensajes';

  @override
  String todayTime(Object time) {
    return 'Hoy, $time';
  }

  @override
  String yesterdayTime(Object time) {
    return 'Ayer, $time';
  }

  @override
  String get callHistoryRetention => 'Almacenamiento del historial de llamadas';

  @override
  String retentionPeriodDescription(num days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Mantener llamadas $days días',
      one: 'Mantener llamadas 1 día',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfinite => 'Mantenga todas las llamadas';

  @override
  String retentionDays(num days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: '$days días',
      one: '1 día',
    );
    return '$_temp0';
  }

  @override
  String get retentionInfiniteOption => 'Sin límites';

  @override
  String get addCommentSpam => 'Añadir comentario (opcional)';

  @override
  String get commentHintSpam =>
      '¿Por qué es spam? ¿De qué se trataba la llamada? Por favor, sea educado.';

  @override
  String get addCommentLegitimate => 'Añadir comentario (opcional)';

  @override
  String get commentHintLegitimate =>
      '¿Por qué es legítimo? ¿Quién le ha llamado? Por favor, sea educado.';

  @override
  String get serverSettings => 'Configuración del servidor';

  @override
  String get serverSettingsDescription =>
      'Gestionar la configuración de tu cuenta PhoneBlock';

  @override
  String get searchNumber => 'Número de búsqueda';

  @override
  String get searchPhoneNumber => 'Buscar número de teléfono';

  @override
  String get enterPhoneNumber => 'Introduzca el número de teléfono';

  @override
  String get phoneNumberHint => 'p. ej. +49 123 456789';

  @override
  String get search => 'Buscar en';

  @override
  String get invalidPhoneNumber => 'Introduzca un número de teléfono válido';

  @override
  String get blacklistTitle => 'Lista negra';

  @override
  String get blacklistDescription => 'Números bloqueados';

  @override
  String get whitelistTitle => 'Lista blanca';

  @override
  String get whitelistDescription => 'Números que ha marcado como legítimos';

  @override
  String get blacklistEmpty => 'Su lista negra está vacía';

  @override
  String get whitelistEmpty => 'Su lista blanca está vacía';

  @override
  String get blacklistEmptyHelp =>
      'Fügen Sie Nummern hinzu, indem Sie unerwünschte Anrufe als Spam melden.';

  @override
  String get whitelistEmptyHelp =>
      'Fügen Sie Nummern hinzu, indem Sie blockierte Anrufe als legitim melden.';

  @override
  String get errorLoadingList => 'Error al cargar la lista';

  @override
  String get numberRemovedFromList => 'Número eliminado';

  @override
  String get errorRemovingNumber => 'Error al eliminar el número';

  @override
  String get confirmRemoval => 'Confirmar eliminación';

  @override
  String confirmRemoveFromBlacklist(Object phone) {
    return '¿Quitar $phone de la lista negra?';
  }

  @override
  String confirmRemoveFromWhitelist(Object phone) {
    return '¿Quitar $phone de la lista blanca?';
  }

  @override
  String get remove => 'Eliminar';

  @override
  String get retry => 'Inténtalo de nuevo';

  @override
  String get editComment => 'Editar comentario';

  @override
  String get commentLabel => 'Comentario';

  @override
  String get commentHint => 'Añadir una nota a este número';

  @override
  String get save => 'Guardar';

  @override
  String get commentUpdated => 'Comentario actualizado';

  @override
  String get errorUpdatingComment => 'Error al actualizar el comentario';
}
