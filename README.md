📌 dinner_planner_server – REST-Backend

Dieses Repository enthält den REST-Server des DinnerPlanner-Projekts.
Er stellt die HTTP-Schnittstellen bereit und greift dabei auf die Geschäftslogik des Domain-Modells zu.

Der Server nutzt das JPA-Domain-Modell aus dem verbundenen Projekt dinner_planner nd stellt die Daten über HTTP/JSON-Schnittstellen extern zur Verfügung

***

🔗 Zusammenhang der Module

Der DinnerPlanner besteht aus mehreren Komponenten:

Domain-Model & JPA-Backend
→ Repository: dinner_planner

REST-Server (dieses Projekt)
→ stellt HTTP/JSON-Schnittstellen bereit

Frontend (entsteht in Kürze)
→ konsumiert die bereitgestellten REST-Services

Der Server ist so ausgelegt, dass er das Modellmodul nutzt und darüber Menüstrukturen, Rezepte, Personen und weitere Entitäten bereitstellt.

***

🔧 Technologien

- Java 17
- Jakarta EE / JAX-RS (REST)
- Einbindung des JPA-Domain-Modells (aus dem Projekt dinner_planner)
- Einbindung des Jakarta-Validation-Modells (aus dem Projekt dinner_planner)
- Eclipse-basierte Entwicklungsumgebung

🧩 Einsatz & Zweck

Der Server dient als Grundlage für:

- das Üben und Vertiefen von REST-Architekturen
- die Arbeit mit JPA-basierten Entitäten im Team
- die Anbindung eines Frontends über saubere JSON-APIs
- die Vorbereitung auf reale Backend-Entwicklungsprojekte

Das Projekt entstand parallel zur Umschulung zum Fachinformatiker Anwendungsentwicklung und wurde auf eigene Initiative begonnen. Es wird gemeinschaftlich im Team weitergeführt.

***

👥 Beteiligte

Entwicklung im kleinen Team mit:

- Angela Schlieben
- Ali Abukel
- Andreas Scherer

👤 Autor

Andreas Scherer (2025)
Fachinformatiker für Anwendungsentwicklung (in Ausbildung)
