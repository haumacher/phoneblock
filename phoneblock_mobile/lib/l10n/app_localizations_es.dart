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
  String get missed => 'Falta';

  @override
  String votes(int count) {
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
  String minSpamReportsDescription(int count) {
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
  String minSpamReportsInRangeDescription(int count) {
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
  String pendingCallsNotification(int count) {
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
  String reportedAsLegitimate(String phoneNumber) {
    return '$phoneNumber reportado como legítimo';
  }

  @override
  String reportError(String error) {
    return 'Error al informar: $error';
  }

  @override
  String reportedAsSpam(String phoneNumber) {
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
  String callsBlockedAfterReports(int count) {
    return 'Las llamadas se bloquean a partir de $count mensajes';
  }

  @override
  String rangesBlockedAfterReports(int count) {
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
  String tokenVerificationFailed(String error) {
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
  String ratePhoneNumber(String phoneNumber) {
    return 'Tarifa $phoneNumber';
  }

  @override
  String reportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count reclamaciones',
      one: '1 Denuncia',
    );
    return '$_temp0';
  }

  @override
  String rangeReportsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Reclamaciones en el intervalo de números.',
      one: '1 Denuncia en el intervalo de números',
    );
    return '$_temp0';
  }

  @override
  String legitimateReportsCount(int count) {
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
  String todayTime(String time) {
    return 'Hoy, $time';
  }

  @override
  String yesterdayTime(String time) {
    return 'Ayer, $time';
  }

  @override
  String get callHistoryRetention => 'Almacenamiento del historial de llamadas';

  @override
  String retentionPeriodDescription(int days) {
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
  String retentionDays(int days) {
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
      'Añade números denunciando las llamadas no deseadas como spam.';

  @override
  String get whitelistEmptyHelp =>
      'Añade números informando de llamadas bloqueadas como legítimas.';

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

  @override
  String get appearance => 'Apariencia';

  @override
  String get themeMode => 'Diseño';

  @override
  String get themeModeDescription => 'Elige un diseño claro u oscuro';

  @override
  String get themeModeSystem => 'Sistema estándar';

  @override
  String get themeModeLight => 'Luz';

  @override
  String get themeModeDark => 'Oscuro';

  @override
  String get experimentalFeatures => 'Funciones experimentales';

  @override
  String get answerbotFeature => 'Contestador automático (Answerbot)';

  @override
  String get answerbotFeatureDescription =>
      'Experimental: Gestión del contestador SPAM del buzón Fritz en la aplicación';

  @override
  String get answerbotMenuTitle => 'Contestador automático';

  @override
  String get answerbotMenuDescription => 'Gestionar el contestador SPAM';

  @override
  String potentialSpamLabel(String rating) {
    return 'Sospechoso: $rating';
  }

  @override
  String get statistics => 'Estadísticas';

  @override
  String get blockedCallsCount => 'Llamadas bloqueadas';

  @override
  String get suspiciousCallsCount => 'Llamadas sospechosas';

  @override
  String get fritzboxTitle => 'Caja Fritz';

  @override
  String get fritzboxConnected => 'Conectado';

  @override
  String get fritzboxOffline => 'No disponible';

  @override
  String get fritzboxError => 'Error de conexión';

  @override
  String get fritzboxNotConfiguredShort => 'No configurado';

  @override
  String get fritzboxNotConfigured => 'No se ha configurado Fritz Box';

  @override
  String get fritzboxNotConfiguredDescription =>
      'Conecta tu Fritz Box para ver las llamadas de tu teléfono fijo.';

  @override
  String get fritzboxConnect => 'Conectar Fritz Box';

  @override
  String get fritzboxDisconnect => 'Desconectar Fritz Box';

  @override
  String get fritzboxDisconnectTitle => '¿Desconectar la caja de Fritz?';

  @override
  String get fritzboxDisconnectMessage =>
      'Se borran las llamadas guardadas y los datos de acceso.';

  @override
  String get fritzboxSyncNow => 'Sincronizar ahora';

  @override
  String get fritzboxSyncDescription =>
      '¡Recuperar la lista de llamadas del Fritz!';

  @override
  String fritzboxSyncComplete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nuevas llamadas sincronizadas.',
      one: '1 nueva llamada sincronizada',
      zero: 'No hay nuevas llamadas',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxSyncError => 'Error durante la sincronización';

  @override
  String get fritzboxVersion => 'Versión FRITZ OS';

  @override
  String get fritzboxHost => 'Dirección';

  @override
  String get fritzboxCachedCalls => 'Llamadas guardadas';

  @override
  String get fritzboxLastSync => 'Última sincronización';

  @override
  String get fritzboxJustNow => 'Ahora mismo';

  @override
  String fritzboxMinutesAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Antes de $count minutos',
      one: 'hace 1 minuto',
    );
    return '$_temp0';
  }

  @override
  String fritzboxHoursAgo(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count horas atrás',
      one: 'hace 1 hora',
    );
    return '$_temp0';
  }

  @override
  String get fritzboxWizardTitle => 'Conectar Fritz Box';

  @override
  String get fritzboxStepDetection => 'Encontrar Fritz Box';

  @override
  String get fritzboxStepDetectionSubtitle => 'Búsqueda automática en la red';

  @override
  String get fritzboxStepLogin => 'Conectarse';

  @override
  String get fritzboxStepLoginSubtitle => 'Introducir datos de acceso';

  @override
  String get fritzboxSearching => 'Busca Fritz Box...';

  @override
  String get fritzboxNotFound => 'Caja no encontrada';

  @override
  String get fritzboxNotFoundDescription =>
      'No se ha podido encontrar el Fritz!Box automáticamente. Por favor, introduzca la dirección manualmente.';

  @override
  String get fritzboxHostLabel => 'Dirección del buzón';

  @override
  String get fritzboxRetrySearch => 'Buscar de nuevo';

  @override
  String get fritzboxManualConnect => 'Conectar';

  @override
  String get fritzboxLoginDescription =>
      'Introduzca sus datos de acceso a Fritz!Box. Puede encontrarlos en la interfaz de usuario de Fritz!Box en Sistema > Usuario de Fritz!Box.';

  @override
  String get fritzboxUsernameLabel => 'Nombre de usuario';

  @override
  String get fritzboxUsernameHint => 'admin o su usuario de Fritz!Box';

  @override
  String get fritzboxPasswordLabel => 'Contraseña';

  @override
  String get fritzboxCredentialsNote =>
      'Tus datos de acceso se almacenan de forma segura en tu dispositivo.';

  @override
  String get fritzboxTestAndSave => 'Pruebas y ahorro';

  @override
  String get fritzboxConnectionFailed =>
      'Error de conexión. Por favor, compruebe los datos de acceso.';

  @override
  String get fritzboxFillAllFields => 'Rellene todos los campos.';

  @override
  String get fritzboxOfflineBanner =>
      'Fritz! buzón no localizable - mostrar llamadas guardadas';

  @override
  String get sourceMobile => 'Móvil';

  @override
  String get sourceFritzbox => 'Caja Fritz';

  @override
  String get fritzboxStepBlocklist => 'Protección contra el spam';

  @override
  String get fritzboxStepBlocklistSubtitle => 'Configurar la lista de bloqueo';

  @override
  String get fritzboxBlocklistDescription =>
      'Seleccione cómo debe protegerse su Fritz!Box contra las llamadas de spam.';

  @override
  String get fritzboxCardDavTitle => 'Lista de bloqueo CardDAV';

  @override
  String get fritzboxCardDavDescription =>
      'Fritz!Box sincroniza la lista de bloqueo directamente con PhoneBlock. Recomendado para FRITZ!OS 7.20+.';

  @override
  String get fritzboxSkipBlocklist => 'Configurar más tarde';

  @override
  String get fritzboxSkipBlocklistDescription =>
      'Puedes activar la protección antispam más adelante en los ajustes.';

  @override
  String get fritzboxVersionTooOldForCardDav =>
      'CardDAV requiere FRITZ!OS 7.20 o superior. Su Fritz!Box tiene una versión anterior.';

  @override
  String get fritzboxFinishSetup => 'Finalizar la configuración';

  @override
  String get fritzboxPhoneBlockNotLoggedIn =>
      'Inicia sesión en PhoneBlock primero.';

  @override
  String get fritzboxCannotGetUsername =>
      'No se ha podido recuperar el nombre de usuario de PhoneBlock.';

  @override
  String get fritzboxBlocklistConfigFailed =>
      'No se ha podido configurar la lista de bloqueo.';

  @override
  String get fritzboxCardDavStatus => 'Estado de CardDAV';

  @override
  String get fritzboxCardDavStatusSynced => 'Sincronizado';

  @override
  String get fritzboxCardDavStatusPending => 'Sincronización pendiente';

  @override
  String get fritzboxCardDavStatusError => 'Error de sincronización';

  @override
  String get fritzboxCardDavStatusDisabled => 'Desactivado';

  @override
  String get fritzboxCardDavNote =>
      'El Fritz!Box sincroniza la agenda telefónica una vez al día a medianoche.';

  @override
  String get fritzboxBlocklistMode => 'Modo de protección contra el spam';

  @override
  String get fritzboxBlocklistModeCardDav =>
      'CardDAV (sincronización automática)';

  @override
  String get fritzboxBlocklistModeNone => 'No activado';

  @override
  String get fritzboxEnableCardDav => 'Activar CardDAV';

  @override
  String get fritzboxEnableCardDavDescription =>
      'Sincroniza la lista de bloqueo de spam directamente con Fritz!Box';

  @override
  String get fritzboxCardDavEnabled => 'Lista de bloqueo CardDAV activada';

  @override
  String get fritzboxDisableCardDav => 'Desactivar CardDAV';

  @override
  String get fritzboxDisableCardDavTitle => '¿Desactivar CardDAV?';

  @override
  String get fritzboxDisableCardDavMessage =>
      '¡La lista de bloqueo CardDAV se elimina de la base de datos de Fritz!';

  @override
  String get fritzboxDisable => 'Desactivar';

  @override
  String get fritzboxCardDavDisabled => 'Lista de bloqueo CardDAV desactivada';
}
