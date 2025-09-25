package com.example.taskapp.it;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import com.example.taskapp.dto.PageResponse;
import com.example.taskapp.dto.TaskCreateReq;
import com.example.taskapp.dto.TaskResp;
import com.example.taskapp.dto.TaskUpdateReq;
import com.example.taskapp.model.TaskStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Task API 統合テスト（TestRestTemplate＋H2実DB）")
@Sql(scripts = "/testdata/clean.sql", config = @SqlConfig(encoding = "UTF-8"), executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TaskApiTest {

    @Autowired
    TestRestTemplate rest;

    private TaskResp createTask(String title, String desc, TaskStatus status, LocalDate due) {
        TaskCreateReq req = new TaskCreateReq();
        req.setTitle(title);
        req.setDescription(desc);
        req.setStatus(status);
        req.setDueDate(due);
        ResponseEntity<TaskResp> res = rest.postForEntity("/api/tasks", req, TaskResp.class);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(res.getBody()).isNotNull();
        return res.getBody();
    }

    @Test
    @DisplayName("正常系: POST /api/tasks で201と作成内容を返す")
    void create_returns_201_and_body() {
        TaskResp created = createTask("Create IT", "Body", TaskStatus.OPEN, LocalDate.now());
        assertThat(created.getId()).isNotNull();
        assertThat(created.getVersion()).isEqualTo(0L);
        assertThat(created.getStatus()).isEqualTo(TaskStatus.OPEN);
    }

    @Test
    @DisplayName("正常系: GET /api/tasks/{id} で200と該当データ")
    void get_by_id_returns_200() {
        TaskResp created = createTask("Get IT", "", TaskStatus.DONE, LocalDate.now());
        ResponseEntity<TaskResp> res = rest.getForEntity("/api/tasks/" + created.getId(), TaskResp.class);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getTitle()).isEqualTo("Get IT");
    }

    @Test
    @DisplayName("異常系: GET /api/tasks/{id} 存在しないIDは404")
    void get_not_found_returns_404() {
        ResponseEntity<TaskResp> res = rest.getForEntity("/api/tasks/999999", TaskResp.class);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("異常系: PUT /api/tasks/{id} If-Match未指定で400")
    void put_without_if_match_returns_400() {
        TaskResp created = createTask("No IfMatch", "", TaskStatus.OPEN, LocalDate.now());

        TaskUpdateReq body = new TaskUpdateReq();
        body.setTitle("Updated");
        body.setDescription("U");
        body.setStatus(TaskStatus.DOING);
        body.setDueDate(LocalDate.now());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TaskUpdateReq> entity = new HttpEntity<>(body, headers);

        ResponseEntity<TaskResp> res = rest.exchange("/api/tasks/" + created.getId(), HttpMethod.PUT, entity, TaskResp.class);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("正常系: PUT /api/tasks/{id} If-Match一致で200・ETag数値・version+1")
    void put_with_if_match_success_returns_200_and_etag_and_version_incremented() {
        TaskResp created = createTask("To Update", "", TaskStatus.OPEN, LocalDate.now());

        TaskUpdateReq body = new TaskUpdateReq();
        body.setTitle("Updated Title");
        body.setDescription("Updated Desc");
        body.setStatus(TaskStatus.DONE);
        body.setDueDate(LocalDate.now());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("If-Match", String.valueOf(created.getVersion()));
        HttpEntity<TaskUpdateReq> entity = new HttpEntity<>(body, headers);

        ResponseEntity<TaskResp> res = rest.exchange("/api/tasks/" + created.getId(), HttpMethod.PUT, entity, TaskResp.class);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getHeaders().getFirst(HttpHeaders.ETAG)).isEqualTo("1");
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getVersion()).isEqualTo(1L);
        assertThat(res.getBody().getTitle()).isEqualTo("Updated Title");
    }

    @Test
    @DisplayName("異常系: PUT /api/tasks/{id} If-Match不一致で409")
    void put_conflict_returns_409() {
        TaskResp created = createTask("Conflict", "", TaskStatus.OPEN, LocalDate.now());

        TaskUpdateReq body1 = new TaskUpdateReq();
        body1.setTitle("Once");
        body1.setDescription("1");
        body1.setStatus(TaskStatus.DOING);
        body1.setDueDate(LocalDate.now());
        HttpHeaders h1 = new HttpHeaders();
        h1.setContentType(MediaType.APPLICATION_JSON);
        h1.add("If-Match", "0");
        rest.exchange("/api/tasks/" + created.getId(), HttpMethod.PUT, new HttpEntity<>(body1, h1), TaskResp.class);

        TaskUpdateReq body2 = new TaskUpdateReq();
        body2.setTitle("Twice");
        body2.setDescription("2");
        body2.setStatus(TaskStatus.DONE);
        body2.setDueDate(LocalDate.now());
        HttpHeaders h2 = new HttpHeaders();
        h2.setContentType(MediaType.APPLICATION_JSON);
        h2.add("If-Match", "0");

        ResponseEntity<TaskResp> res = rest.exchange("/api/tasks/" + created.getId(), HttpMethod.PUT, new HttpEntity<>(body2, h2), TaskResp.class);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    @DisplayName("正常系: DELETE /api/tasks/{id} で204、以後GETは404")
    void delete_returns_204_and_then_404_on_get() {
        TaskResp created = createTask("To Delete", "", TaskStatus.DONE, LocalDate.now());

        ResponseEntity<Void> del = rest.exchange("/api/tasks/" + created.getId(), HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);
        assertThat(del.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<TaskResp> getAfter = rest.getForEntity("/api/tasks/" + created.getId(), TaskResp.class);
        assertThat(getAfter.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("正常系: GET /api/tasks の検索・ページング（status+q、created_at DESC）")
    @Sql(scripts = "/testdata/task_seed.sql", config = @SqlConfig(encoding = "UTF-8"), executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void search_with_filters_and_paging() {
        ParameterizedTypeReference<PageResponse<TaskResp>> type = new ParameterizedTypeReference<>() {};

        ResponseEntity<PageResponse<TaskResp>> p1 = rest.exchange(
                "/api/tasks?status=OPEN&q=foo&page=0&size=1",
                HttpMethod.GET,
                null,
                type);
        assertThat(p1.getStatusCode().value()).isEqualTo(200);
        assertThat(p1.getBody()).isNotNull();
        assertThat(p1.getBody().content()).hasSize(1);
        assertThat(p1.getBody().total()).isEqualTo(2);
        assertThat(p1.getBody().content().get(0).getTitle()).isEqualTo("Another foo");

        ResponseEntity<PageResponse<TaskResp>> p2 = rest.exchange(
                "/api/tasks?status=OPEN&q=foo&page=1&size=1",
                HttpMethod.GET,
                null,
                type);
        assertThat(p2.getStatusCode().value()).isEqualTo(200);
        assertThat(p2.getBody()).isNotNull();
        assertThat(p2.getBody().content()).hasSize(1);
        assertThat(p2.getBody().content().get(0).getTitle()).isEqualTo("Alpha task");
    }
}

