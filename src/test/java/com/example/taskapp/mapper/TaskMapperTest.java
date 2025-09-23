package com.example.taskapp.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import com.example.taskapp.model.Task;
import com.example.taskapp.model.TaskStatus;

@MybatisTest
@DisplayName("TaskMapperのMyBatisスライステスト（H2実DB）")
@Sql(scripts = "/db/migration/V1__init.sql", config = @SqlConfig(encoding = "UTF-8"), executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/testdata/clean.sql", config = @SqlConfig(encoding = "UTF-8"), executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TaskMapperTest {

    @Autowired
    TaskMapper mapper;

    @Test
    @DisplayName("insertとfindById: 正常に登録され、採番IDで取得できる")
    void insert_and_findById() {
        Task t = Task.builder()
                .title("New Task")
                .description("New Desc")
                .status(TaskStatus.OPEN)
                .dueDate(LocalDate.now())
                .build();

        int inserted = mapper.insert(t);
        assertThat(inserted).isEqualTo(1);
        assertThat(t.getId()).isNotNull();

        Task found = mapper.findById(t.getId());
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("New Task");
        assertThat(found.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(found.getVersion()).isEqualTo(0L);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("search/count: status+キーワードの組合せとページングが機能する")
    @Sql(scripts = "/testdata/task_seed.sql", config = @SqlConfig(encoding = "UTF-8"), executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void search_with_filters_and_paging() {
        List<Task> page1 = mapper.search(TaskStatus.OPEN, "foo", 0, 1);
        assertThat(page1).hasSize(1);
        assertThat(page1.get(0).getTitle()).isEqualTo("Another foo"); // created_at DESC で新しい方

        List<Task> page2 = mapper.search(TaskStatus.OPEN, "foo", 1, 1);
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).getTitle()).isEqualTo("Alpha task");

        long cnt = mapper.count(TaskStatus.OPEN, "foo");
        assertThat(cnt).isEqualTo(2);
    }

    @Test
    @DisplayName("updateWithOptimisticLock: 成功時はversion+1、競合時は0件更新")
    @Sql(scripts = "/testdata/task_seed.sql", config = @SqlConfig(encoding = "UTF-8"), executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void update_with_optimistic_lock_success_and_conflict() {
        // まず 'Alpha task' を検索して取得
        List<Task> all = mapper.search(null, "Alpha", 0, 10);
        assertThat(all).hasSize(1);
        Task alpha = all.get(0);
        assertThat(alpha.getVersion()).isEqualTo(0L);

        // 正常更新（version一致）
        alpha.setTitle("Alpha updated");
        alpha.setStatus(TaskStatus.DOING);
        int updated = mapper.updateWithOptimisticLock(alpha);
        assertThat(updated).isEqualTo(1);

        Task after = mapper.findById(alpha.getId());
        assertThat(after.getTitle()).isEqualTo("Alpha updated");
        assertThat(after.getVersion()).isEqualTo(1L);

        // 旧バージョンで再度更新 → 競合で0件
        alpha.setTitle("Alpha conflict");
        alpha.setVersion(0L); // 旧版のまま
        int conflict = mapper.updateWithOptimisticLock(alpha);
        assertThat(conflict).isEqualTo(0);
    }

    @Test
    @DisplayName("deleteById: 1件削除後に取得不可になる")
    void delete_by_id() {
        Task t = Task.builder()
                .title("To delete")
                .description("temp")
                .status(TaskStatus.DONE)
                .dueDate(LocalDate.now())
                .build();
        mapper.insert(t);

        int deleted = mapper.deleteById(t.getId());
        assertThat(deleted).isEqualTo(1);
        assertThat(mapper.findById(t.getId())).isNull();
    }
}

