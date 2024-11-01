# Verzeichnis mit Audiodateien, aus denen PhoneBlock einen Dialog mit dem Anrufer zusammensetzt

Ein PhoneBlock-Dialog besteht aus folgenden Komponenten:
 * Eine Begrüßung ("hello")
 * Warten auf eine Meldung des Gegenüber ("waiting")
 * Eine Rückfrage ("question")
 * Warten auf eine Antwort ("waiting")
 * Weiter bei "Rückfrage".
 * Eine Nachfrage, wenn sich das Gegenüber auf die Begrüßung nicht meldet ("who-is-calling")
 * Eine Nachfrage, wenn das Gegenüber auf eine Rückfrage nicht antwortet ("still-there")

Für jeden Dialog-Teil gibt es ein Unterverzeichnis, in den eine Audiodatei (WAV) abgelegt werden muss, in der (am 
besten) viele mögliche Sätze gesprochen werden, die zur jeweiligen Situation des Dialoges passen. Zwischen jedem Satz 
muss sich ausreichend Stille (1s) befinden, damit PhoneBlock die Sätze voneinander trennen kann. Die folgenden 
Abschnitte machen Vorschläge, was man in den jeweiligen Audio-Dateien aufsprechen könnte.

Wenn die Audiodateien vorliegen, muss das Dialogverzeichnis mit dem Tool `ConversionInitializer` initialisiert werden.
Diese Tool splittet die Audiodateien in die einzelnen Sätze und bereite verschiedene Formatversionen vor, die für die 
Kommunikation benötigt werden (je nachdem was für ein Audioformat mit der Gegenstelle ausgehandelt wird).
  

## Ansagen, mit denen das Gespräch begonnen wird ("hello")
 * Guten Tag, wie kann ich Ihnen behilflich sein?
 * Guten Tag, was kann ich für Sie tun?
 * Hallo, wie kann ich Ihnen weiterhelfen?
 * Einen schönen guten Tag, mit wem spreche ich?
 * Einen schönen guten Tag, wer ist da bitte?
 * Einen schönen guten Tag, was wünschen Sie?

## Nach der Ansage meldet sich das Call-Center nicht sofort, PhoneBlock fragt nach, wer anruft ("who-is-calling")

 * Hallo, wer ist da bitte?
 * Hallo, wer ist in der Leitung?
 * Hallo, ist da jemand?
 * Hallo, bitte melden Sie sich!
 * Ich höre nichts, wer sind Sie?
 * Ist da jemand? Ich höre nichts!

## Das Gegenüber sagt nichts, PhoneBlock stellt eine Nachfrage ("still-there")

 * Entschuldigen Sie, aber ich höre nichts. Können Sie mich hören?                            
 * Gibt es ein Problem mit der Verbindung? Haben Sie etwas gesagt?             
 * Ist die Verbindung unterbrochen? Hören Sie mich?            
 * Ich habe Sie nicht verstanden, können Sie das nocheinmal wiederholen?                    
 * Es scheint, als ob es Kommunikationsprobleme gibt. Könnten Sie noch einmal anrufen?                      
 * Tut mir leid, aber ich kann nichts hören. Können Sie bitte wiederholen, was Sie gesagt haben?  
 * Gibt ein technisches Problem mit der Verbindung? Ich höre Sie nicht!
 * Es sieht so aus, als hätten wir ein Verbindungsproblem. Haben Sie etwas gesagt?                            

## Das Gegenüber hat etwas gesagt, PhoneBlock stellt eine idiotische Rückfrage ("question")
    
 * Können Sie das bitte genauer erklären?
 * Können Sie mir mehr Informationen dazu geben?
 * Lassen Sie mich darüber nachdenken - muss das jetzt sofort entschieden werden?
 * Können Sie Beispiele dafür nennen?
 * Ich verstehe noch nicht ganz, können Sie das nocheinmal erklären?
 * Ich bin mir nicht sicher, können Sie weitere Einzelheiten mitteilen?
 * Gibt es noch etwas, was Sie hinzufügen möchten?
 * Können Sie bitte mehr Details dazu geben?
 * Könnten Sie das bitte genauer erklären?
 * Gibt es weitere Informationen, die relevant sein könnten?
 * Könnten Sie mir mehr Hintergrundinformationen geben?
 * Ist das unbedingt notwendig? Welche anderen Optionen können in Betracht gezogen werden?
 * Das kann ich mir nicht vorstellen, könnten Sie das bitte ausführlicher erläutern?
 * Das klingt sehr allgemein. Welche genauen Daten haben Sie hier?
 * Gibt es zusätzliche Fakten, die berücksichtigt werden sollten?
 * Können Sie das genauer in einzelnen Schritten erklären?
 
## Während PhoneBlock auf eine Antwort wartet, wird Rauschen abgespielt, um eine Verbindung vorzutäuschen ("waiting")

 * Eine Audio-Datei, in der einfach für 3 bis 5 Sekunden Stille (am besten mit ganz leichtem Grundrauschen) aufgenommen 
   wird.
 
 
 