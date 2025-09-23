# AGENTS.md — Spring Boot + MyBatis CRUD API（JUnitテスト教材用）

> 目的：**JUnit テスト実装サンプル**のための、フロント不要・バックエンド専用の **Spring Boot + MyBatis + Maven** CRUD API を自動生成・自動整備する。現段階は **H2 でアプリ実装および全テスト**を実施する（**Oracle 対応は後課題**／本フェーズでは **Testcontainers は未導入**）。CI は **Jenkins** を想定。設定ファイルは **application.properties** に統一する。

---

## 🎯 ゴール / 成果物

* 動作する **Task（ToDo）CRUD REST API** 一式
* 層構造：Controller / Service / Mapper（MyBatis） / Domain / DTO / Exception
* **Bean Validation**、**楽観ロック（version + If-Match/ETag）**、**検索 + ページング**
* **Flyway** による DB マイグレーション
* **テスト一式**：

  * ユニット（Mockito）
  * スライス（`@WebMvcTest`, `@MybatisTest`）
  * 統合（`@SpringBootTest` + TestRestTemplateによるAPIテスト）
* **Maven 設定**（Surefire / Failsafe / JaCoCo）
* **Jenkinsfile**（段階パイプライン、JUnit/JaCoCo レポート公開）
* 任意：Dockerfile（アプリ起動用）

> **完了条件（Definition of Done）** は本書末尾の ✅ チェックリストに従うこと。

---

## 📦 技術スタック & バージョン

* Java 21
* Spring Boot 3.x
* H2 DataBase
* MyBatis + mybatis-spring-boot-starter
* Flyway
* JUnit 5, Mockito
* Lombok（`@Data` 利用可）
* Maven 3.9+

> **方針**：H2データベース で実装、ユニットテスト、統合テスト実施（Oracle 対応は後課題）。SQLはできるだけANSI準拠で記述

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
   │     ├─ application.properties
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
* `description`: `@Size(max=4000)`（Oracle の `VARCHAR2` 相当）
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
  * `description` カラムは `VARCHAR(4000)`（Oracle の `VARCHAR2(4000)` 相当）

---

## 🧵 MyBatis（Mapper & XML）

* `TaskMapper.java`：`insert`, `findById`, `search`, `updateWithOptimisticLock`, `deleteById`
* XML：

---

## 🧪 テスト戦略（必須）

1. **ユニット**（Mockito）

* 対象：`TaskService`
* 例：楽観ロック成功/失敗、境界値

2. **スライス**

* **`@WebMvcTest(TaskController)`**：HTTP 契約、バリデーション、If-Match/ETag
* **`@MybatisTest`**：Mapper の SQL/マッピング（H2）

3. **統合**（`@SpringBootTest` + TestRestTemplate による API テスト、H2 のみ。Oracle Testcontainers は本フェーズでは未使用）


## 📜 コーディング規約 & 設計ルール（抜粋）

* Controller では **DTO ⇄ Domain 変換**を明確化（`TaskResp.from(domain)`）
* 更新は **If-Match(version)** 必須、レスポンスは **ETag(newVersion)** を設定（ETag は数値そのまま・引用符なしで扱う）
* `DELETE /api/tasks/{id}` は **204 No Content** を返す
* 一覧応答は `{ content, page, size, total }` 形式、既定 `page=0`, `size=20`、`size` 上限は 100、既定ソートは `created_at DESC`
* 例外は **共通ハンドラ**に集約（400/404/409）
* Service は **トランザクション境界**、Mapper は集約ごと
* MyBatis の XML は **N+1** を避け、必要に応じて `fetch size`/`resultOrdered` を設定

### ✅ テスト記述規約（DisplayName）

* すべてのテスト「クラス」と「メソッド」に **`@DisplayName` を付与**し、**日本語**でテスト内容を簡潔に記述する。
  * 形式例: `正常系: ...` / `異常系: ...` / `境界値: ...`
  * 期待結果を含む短文（目安: 50 文字以内）。
  * BDD 形式（Given/When/Then）の採用は任意。必要に応じてコメントで補足可。
* 新規テスト作成時は、この規約を満たすことをレビュー観点に含める。

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

---

## ✅ 品質ゲート & チェックリスト（Codex 用）

* [ ] JaCoCo レポート生成、主要層で **命令 70%+**
* [ ] `POST/PUT/GET/DELETE` で基本 CRUD 動作
* [ ] `PUT` は If-Match 必須、応答に ETag あり、version が +1
* [ ] `GET /api/tasks` の検索・ページングが機能
* [ ] Flyway で DB 初期化（`V1__init.sql`）

---

## 🗒️ 生成順序（推奨）

1. `pom.xml`（依存＆プラグイン）
2. Flyway `V1__init.sql`
3. Domain/DTO/Enum
4. Mapper インターフェース + XML（最低限の CRUD）
5. Service（トランザクション/楽観ロック）
6. Controller（If-Match/ETag） + 例外ハンドラ
7. 各機能作りこみごとに必要なユニットテストクラス作成
8. スライステスト/統合テスト

---

## 🧠 Codex への指示テンプレ（プロンプト）

> 次の要件で、ファイルを **順次** 出力してください。各ファイルはパス・ファイル名・中身を示し、差分管理がしやすいようにコードブロックで提示すること。依存とプラグインは `pom.xml` にまとめ、テストが `mvn verify` で通る状態にしてください。

**要件要約**

* Spring Boot 3.x, Java 21, Maven
* MyBatis（XML マッパー）、Flyway、Lombok（`@Data` 可）
* Task CRUD（title/description/status/dueDate/version/createdAt/updatedAt）
* 楽観ロック（If-Match/ETag、version インクリメント。ETag は数値・非引用）
* 検索 & ページング（status/q/page/size）
* 設定は `application.properties`
* テスト：ユニット（Mockito）、`@WebMvcTest`、`@MybatisTest`（H2）、`@SpringBootTest`（H2）
* JaCoCo レポート（命令網羅 70% 以上）

---

以上を **唯一の情報源（single source of truth）** として生成を進めること。途中成果は必ずビルド・テスト可能な状態で段階出力し、失敗時は差分修正のみを返すこと。
