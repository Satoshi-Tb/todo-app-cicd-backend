# AGENTS.md — Spring Boot + MyBatis CRUD API（JUnitテスト教材用）

> 目的：**JUnit テスト実装サンプル**のための、フロント不要・バックエンド専用の **Spring Boot + MyBatis + Maven** CRUD API を自動生成・自動整備する。**H2 で高速検証**しつつ、**Oracle（Testcontainers）で統合テスト**を実行可能にする。CI は **Jenkins** を想定。

---

## 🎯 ゴール / 成果物

* 動作する **Task（ToDo）CRUD REST API** 一式
* 層構造：Controller / Service / Mapper（MyBatis） / Domain / DTO / Exception
* **Bean Validation**、**楽観ロック（version + If-Match/ETag）**、**検索 + ページング**
* **Flyway** による DB マイグレーション
* **テスト一式**：

  * ユニット（Mockito）
  * スライス（`@WebMvcTest`, `@MybatisTest`）
  * 統合（`@SpringBootTest` + Testcontainers(Oracle)）
* **Maven 設定**（Surefire / Failsafe / JaCoCo）
* **Jenkinsfile**（段階パイプライン、JUnit/JaCoCo レポート公開）
* 任意：Dockerfile（アプリ起動用）

> **完了条件（Definition of Done）** は本書末尾の ✅ チェックリストに従うこと。

---

## 📦 技術スタック & バージョン

* Java 17 / 21（どちらかを `maven-toolchain` で固定）
* Spring Boot 3.x
* MyBatis + mybatis-spring-boot-starter 3.0.3
* Flyway
* JUnit 5, Mockito, AssertJ
* Testcontainers 1.20.x（Oracle: `gvenzl/oracle-free:*faststart*`）
* Maven 3.9+
* Jenkins（Declarative Pipeline）

> **方針**：H2 で軽いテスト、Oracle コンテナで方言依存を担保。

---

## 📁 リポジトリ構成（生成指示）

```
/ (repo root)
├─ pom.xml
├─ Jenkinsfile
├─ Dockerfile                # 任意
├─ README.md
└─ src
   ├─ main
   │  ├─ java/com/example/taskapp
   │  │  ├─ TaskAppApplication.java
   │  │  ├─ controller/TaskController.java
   │  │  ├─ service/TaskService.java
   │  │  ├─ mapper/TaskMapper.java
   │  │  ├─ model/Task.java
   │  │  ├─ dto/TaskCreateReq.java
   │  │  ├─ dto/TaskUpdateReq.java
   │  │  ├─ dto/TaskResp.java
   │  │  ├─ model/TaskStatus.java
   │  │  └─ exception/GlobalExceptionHandler.java
   │  └─ resources
   │     ├─ application.yml
   │     ├─ db/migration/V1__init.sql
   │     └─ mapper/TaskMapper.xml
   └─ test
      ├─ java/com/example/taskapp
      │  ├─ service/TaskServiceTest.java
      │  ├─ controller/TaskControllerTest.java
      │  ├─ mapper/TaskMapperTest.java
      │  └─ it/TaskApiIT.java
      └─ resources/
```

---

## 🧩 ドメイン & API 仕様

### エンティティ（`Task`）

| フィールド       | 型                 | 必須 | 説明              |
| ----------- | ----------------- | -- | --------------- |
| id          | Long              | 生成 | IDENTITY        |
| title       | String(<=200)     | 必須 | タイトル            |
| description | String            | 任意 | 詳細（CLOB 相当）     |
| status      | `TaskStatus`      | 必須 | OPEN/DOING/DONE |
| dueDate     | LocalDate         | 任意 | 期限（将来/今日可）      |
| version     | Long              | 必須 | 楽観ロック用（0 から）    |
| createdAt   | Instant/Timestamp | 必須 | 生成時刻            |
| updatedAt   | Instant/Timestamp | 必須 | 更新時刻            |

### REST エンドポイント

* `POST /api/tasks` … 作成
* `GET /api/tasks/{id}` … 取得
* `PUT /api/tasks/{id}` … 更新（**If-Match: {version}** 必須、応答に `ETag: {newVersion}`）
* `DELETE /api/tasks/{id}` … 削除
* `GET /api/tasks?status=&q=&page=&size=` … 検索 + ページング（`q` は title/description を部分一致）

### バリデーション

* `title`: `@NotBlank @Size(max=200)`
* `description`: `@Size(max=4000)`（実 DB では CLOB）
* `status`: `@NotNull`
* `dueDate`: `@FutureOrPresent`

### 例外方針

* 404 Not Found（対象なし）
* 409 Conflict（楽観ロック失敗）
* 400 Bad Request（validation）
* 共通ハンドラ：`GlobalExceptionHandler`

---

## 🗃️ DB 設計 & マイグレーション（Flyway）

* `V1__init.sql` を生成：

  * `tasks(id, title, description, status, due_date, version, created_at, updated_at)`
  * Oracle を前提に型設計（H2 でも通るよう配慮）
  * `version` は `NUMBER DEFAULT 0 NOT NULL`
  * `created_at/updated_at` は `TIMESTAMP`、更新時はトリガ or アプリ更新（本サンプルはアプリ側で `SYSTIMESTAMP`）

---

## 🧵 MyBatis（Mapper & XML）

* `TaskMapper.java`：`insert`, `findById`, `search`, `updateWithOptimisticLock`, `deleteById`
* XML：

  * `insert` は `useGeneratedKeys=true`（H2/Oracle の差異は注意）
  * `search` は動的 SQL（`status` と `q` の任意指定）
  * `updateWithOptimisticLock` は `WHERE id = #{id} AND version = #{version}` + `version = version + 1, updated_at = SYSTIMESTAMP`

---

## 🧪 テスト戦略（必須）

1. **ユニット**（Mockito / AssertJ）

* 対象：`TaskService`
* 例：楽観ロック成功/失敗、境界値

2. **スライス**

* **`@WebMvcTest(TaskController)`**：HTTP 契約、バリデーション、If-Match/ETag
* **`@MybatisTest`**：Mapper の SQL/マッピング（H2）

3. **統合**（`@SpringBootTest` + **Testcontainers(Oracle)**）

* 例：`TaskApiIT` で POST→GET ラウンドトリップ、Flyway 実行
* イメージ：`gvenzl/oracle-free:23.5-slim-faststart`

> 命名：ユニット/スライスは `*Test.java`（Surefire）、統合は `*IT.java`（Failsafe）。

---

## 🧰 Maven 設定（依存 & プラグイン）

* 依存：

  * `spring-boot-starter-web`, `spring-boot-starter-validation`, `mybatis-spring-boot-starter:3.0.3`, `flyway-core`
  * テスト：`spring-boot-starter-test`, `mybatis-spring-boot-starter-test:3.0.3`, `mockito-junit-jupiter`, `assertj-core`
  * 統合：`testcontainers:junit-jupiter`, `ojdbc11`
* プラグイン：

  * `maven-surefire-plugin`（ユニット/スライス）
  * `maven-failsafe-plugin`（統合：`integration-test`+`verify`）
  * `jacoco-maven-plugin`（`prepare-agent` → `verify` で `report`）
* 最低限カバレッジ閾値（例）：命令 70% / 分岐 60%（`TaskService` など重要層は 80% 以上）

---

## 🐳 Testcontainers（Oracle）設定

* テストクラスに `@Testcontainers`、`@Container static GenericContainer<?> oracle = new GenericContainer<>("gvenzl/oracle-free:23.5-slim-faststart") ...` を生成
* `@DynamicPropertySource` で `spring.datasource.url/username/password` を注入
* CI では Docker 実行権限必須、メモリ 2〜4GB 以上
* ローカル高速化（任意）：`~/.testcontainers.properties` に `testcontainers.reuse.enable=true`、Java 側 `.withReuse(true)`

---

## 🚦 Jenkins（Declarative Pipeline）

* 段階：

  1. **Unit/Slice**：`mvn -B -DskipITs test` → `junit` 収集
  2. **Integration**：`mvn -B -Dit.test=*IT verify` → `junit` + `publishHTML`（JaCoCo）
  3. **Package**：`mvn -DskipTests package` → `archiveArtifacts`
* ノード要件：Docker 実行可能（Testcontainers 用）
* キャッシュ：`~/.m2` を永続化

---

## 📜 コーディング規約 & 設計ルール（抜粋）

* Controller では **DTO ⇄ Domain 変換**を明確化（`TaskResp.from(domain)`）
* 更新は **If-Match(version)** 必須、レスポンスは **ETag(newVersion)** を設定
* 例外は **共通ハンドラ**に集約（422/400/404/409）
* Service は **トランザクション境界**、Mapper は集約ごと
* MyBatis の XML は **N+1** を避け、必要に応じて `fetch size`/`resultOrdered` を設定

---

## 🧪 テストケース例（必須・抜粋）

* Service ユニット：

  * `update`: 正常（version +1）、version 不一致で 409
  * `create`: 必須項目欠落で検証例外（DTO バリデーション）
* Web スライス：

  * `POST /api/tasks`: 201/Body
  * `PUT /api/tasks/{id}`: If-Match 未指定 → 400、指定 → 200 + `ETag`
  * `GET /api/tasks?q=...&status=...`: ページング/絞り込み
* Mapper スライス：

  * `insert/select`、`search`（status + q 条件組み合わせ）
* 統合（Oracle）：

  * POST→GET ラウンドトリップ（Flyway が先に走ること）

---

## 🧾 生成すべき主要ファイルの雛形（要約）

* `TaskController.java`：REST + If-Match/ETag、`@Valid`
* `TaskService.java`：`create/find/update/delete/search`、楽観ロック
* `TaskMapper.java` + `TaskMapper.xml`
* `Task*.java`（Domain/DTO/Enum）
* `GlobalExceptionHandler.java`
* `V1__init.sql`（Oracle/H2 両対応を意識）
* テスト：`TaskServiceTest`, `TaskControllerTest`, `TaskMapperTest`, `TaskApiIT`
* `pom.xml`（依存＆プラグイン完備）
* `Jenkinsfile`（段階・レポート）
* `README.md`（セットアップ & 実行手順）

---

## 🧭 実行コマンド（ローカル）

```bash
# ユニット/スライスのみ
mvn -DskipITs test

# 統合込み（Oracle Testcontainers 起動）
mvn verify

# アプリ起動（H2 プロファイル例）
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## ✅ 品質ゲート & チェックリスト（Codex 用）

* [ ] `mvn -DskipITs test` がローカルで通る（ユニット/スライス）
* [ ] `mvn verify` がローカルで通る（Oracle Testcontainers 起動、統合テスト成功）
* [ ] JaCoCo レポート生成、主要層で **命令 70%+**
* [ ] `POST/PUT/GET/DELETE` で基本 CRUD 動作
* [ ] `PUT` は If-Match 必須、応答に ETag あり、version が +1
* [ ] `GET /api/tasks` の検索・ページングが機能
* [ ] Flyway で DB 初期化（`V1__init.sql`）
* [ ] Jenkinsfile で段階構成 & レポート公開
* [ ] README に実行/テスト/CI 手順を記載

---

## 🗒️ 生成順序（推奨）

1. `pom.xml`（依存＆プラグイン）
2. Domain/DTO/Enum
3. Mapper インターフェース + XML（最低限の CRUD）
4. Service（トランザクション/楽観ロック）
5. Controller（If-Match/ETag） + 例外ハンドラ
6. Flyway `V1__init.sql`
7. テスト 3 種（ユニット/スライス/統合）
8. Jenkinsfile / README / （任意）Dockerfile

---

## 🧠 Codex への指示テンプレ（プロンプト）

> 次の要件で、ファイルを **順次** 出力してください。各ファイルはパス・ファイル名・中身を示し、差分管理がしやすいようにコードブロックで提示すること。依存とプラグインは `pom.xml` にまとめ、テストが `mvn verify` で通る状態にしてください。

**要件要約**

* Spring Boot 3.x, Java 17, Maven
* MyBatis（XML マッパー）、Flyway
* Task CRUD（title/description/status/dueDate/version/createdAt/updatedAt）
* 楽観ロック（If-Match/ETag、version インクリメント）
* 検索 & ページング（status/q/page/size）
* テスト：ユニット（Mockito）、`@WebMvcTest`、`@MybatisTest`（H2）、`@SpringBootTest` + Testcontainers(Oracle)
* Jenkinsfile（段階）、JaCoCo レポート

**出力順**

1. `pom.xml`
2. ドメイン/DTO/Enum
3. `TaskMapper.java` / `TaskMapper.xml`
4. `TaskService.java`
5. `TaskController.java` / `GlobalExceptionHandler.java`
6. `V1__init.sql` / `application.yml`
7. テスト 4 本
8. `Jenkinsfile` / `README.md` / （任意）`Dockerfile`

**注意事項**

* H2 で `@MybatisTest` が通るよう型/SQL を調整（関数の差異に注意）。
* Oracle 統合は `gvenzl/oracle-free:*faststart*` を使用し、`@DynamicPropertySource` で Spring 設定を上書き。
* `PUT` は If-Match 強制、成功時は `ETag` を返却。
* 例外は一元ハンドリングし、HTTP ステータスと JSON メッセージを返却。
* JaCoCo レポート（`target/site/jacoco/index.html`）を生成。

---

## 📚 README.md 内容（Codex で自動生成）

* 目的と構成
* 前提ツール（JDK/Maven/Docker）
* セットアップ・ビルド・テスト・実行手順
* Jenkins セットアップの概要（ノード要件、コマンド、レポート場所）
* よくある問題（Testcontainers の Docker 権限、メモリ不足、Oracle 起動待機など）

---

## 🧯 トラブルシュート（短縮版）

* **Oracle コンテナが起動しない**：ノードメモリ 2〜4GB、`faststart` タグ、ログ待機条件を確認
* **H2 と Oracle の差異**：日付/関数/シーケンス/ページング構文に注意。必要なら SQL を方言分岐
* **Generated Keys**：Oracle の ID 自動採番は IDENTITY かシーケンス + trigger。サンプルは IDENTITY 前提
* **CI で Docker 不可**：DinD/Socket 共有/Testcontainers Cloud を検討

---

## 📌 付録：Jenkinsfile（要件）

* `Unit/Slice` → `Integration` → `Package` の 3 段
* `junit` で surefire/failsafe レポート収集
* `publishHTML` で `target/site/jacoco/index.html`
* `archiveArtifacts: target/*.jar`

---

以上を **唯一の情報源（single source of truth）** として生成を進めること。途中成果は必ずビルド・テスト可能な状態で段階出力し、失敗時は差分修正のみを返すこと。
