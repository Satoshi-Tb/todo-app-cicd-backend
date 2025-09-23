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
* Maven 3.9+

> **方針**：H2データベース で実装、ユニットテスト、統合テスト実施。SQLはできるだけANSI準拠で記述

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

3. **統合**（`@SpringBootTest` + TestRestTemplateによるAPIテスト）


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

* Spring Boot 3.x, Java 17, Maven
* MyBatis（XML マッパー）、Flyway
* Task CRUD（title/description/status/dueDate/version/createdAt/updatedAt）
* 楽観ロック（If-Match/ETag、version インクリメント）
* 検索 & ページング（status/q/page/size）
* テスト：ユニット（Mockito）、`@WebMvcTest`、`@MybatisTest`（H2）、`@SpringBootTest` + Testcontainers(Oracle)
* JaCoCo レポート

---

以上を **唯一の情報源（single source of truth）** として生成を進めること。途中成果は必ずビルド・テスト可能な状態で段階出力し、失敗時は差分修正のみを返すこと。
