package com.example.taskapp.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.taskapp.exception.NotFoundException;
import com.example.taskapp.exception.OptimisticLockException;
import com.example.taskapp.mapper.TaskMapper;
import com.example.taskapp.model.Task;
import com.example.taskapp.model.TaskStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskServiceのユニットテスト（Mockito）")
class TaskServiceTest {

    @Mock
    TaskMapper taskMapper;

    @InjectMocks
    TaskService service;

    @Test
    @DisplayName("正常系: createでversion=0とタイムスタンプ設定後に登録される")
    void create_sets_defaults_and_inserts() {
        Task input = Task.builder()
                .title("New Task")
                .description("Desc")
                .status(TaskStatus.OPEN)
                .dueDate(LocalDate.now())
                .build();

        // insert時にID採番された想定
        doAnswer(inv -> {
            Task arg = inv.getArgument(0);
            arg.setId(1L);
            return 1;
        }).when(taskMapper).insert(any(Task.class));

        Task persisted = Task.builder()
                .id(1L)
                .title("New Task")
                .description("Desc")
                .status(TaskStatus.OPEN)
                .dueDate(input.getDueDate())
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(taskMapper.findById(1L)).thenReturn(persisted);

        Task result = service.create(input);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getVersion()).isEqualTo(0L);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(captor.capture());
        Task insertedArg = captor.getValue();
        assertThat(insertedArg.getVersion()).isEqualTo(0L);
        assertThat(insertedArg.getCreatedAt()).isNotNull();
        assertThat(insertedArg.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("異常系: createにnullを渡すとIllegalArgumentException")
    void create_null_throws() {
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("正常系: getでIDに一致するタスクを返す")
    void get_returns_task() {
        Task t = Task.builder().id(10L).title("T").status(TaskStatus.DONE).version(1L)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(taskMapper.findById(10L)).thenReturn(t);

        Task found = service.get(10L);
        assertThat(found.getId()).isEqualTo(10L);
        verify(taskMapper).findById(10L);
    }

    @Test
    @DisplayName("異常系: getで存在しないIDはNotFoundException")
    void get_not_found_throws() {
        when(taskMapper.findById(999L)).thenReturn(null);
        assertThatThrownBy(() -> service.get(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("正常系: updateでIf-Match一致なら更新されversionが+1の値を返す")
    void update_success_increments_version() {
        long id = 1L;
        long ifMatch = 0L;
        Task req = Task.builder()
                .title("Updated")
                .description("U")
                .status(TaskStatus.DOING)
                .dueDate(LocalDate.now())
                .build();

        when(taskMapper.updateWithOptimisticLock(any(Task.class))).thenReturn(1);
        Task after = Task.builder()
                .id(id).title("Updated").description("U")
                .status(TaskStatus.DOING).dueDate(req.getDueDate())
                .version(1L)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(taskMapper.findById(id)).thenReturn(after);

        Task result = service.update(id, ifMatch, req);
        assertThat(result.getVersion()).isEqualTo(1L);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateWithOptimisticLock(captor.capture());
        Task arg = captor.getValue();
        assertThat(arg.getId()).isEqualTo(id);
        assertThat(arg.getVersion()).isEqualTo(ifMatch);
        assertThat(arg.getTitle()).isEqualTo("Updated");
        assertThat(arg.getStatus()).isEqualTo(TaskStatus.DOING);
    }

    @Test
    @DisplayName("異常系: updateでバージョン不一致はOptimisticLockException")
    void update_conflict_throws() {
        long id = 2L;
        when(taskMapper.updateWithOptimisticLock(any(Task.class))).thenReturn(0);
        when(taskMapper.findById(id)).thenReturn(Task.builder().id(id).version(5L).build());

        Task req = Task.builder().title("x").status(TaskStatus.OPEN).build();
        assertThatThrownBy(() -> service.update(id, 4L, req))
                .isInstanceOf(OptimisticLockException.class)
                .hasMessageContaining("expected=4").hasMessageContaining("actual=5");
    }

    @Test
    @DisplayName("異常系: update対象が存在しない場合はNotFoundException")
    void update_missing_throws_not_found() {
        long id = 3L;
        when(taskMapper.updateWithOptimisticLock(any(Task.class))).thenReturn(0);
        when(taskMapper.findById(id)).thenReturn(null);

        Task req = Task.builder().title("x").status(TaskStatus.OPEN).build();
        assertThatThrownBy(() -> service.update(id, 0L, req))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("3");
    }

    @Test
    @DisplayName("異常系: updateにnullを渡すとIllegalArgumentException")
    void update_null_throws() {
        assertThatThrownBy(() -> service.update(1L, 0L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("正常系: deleteで1件削除できる")
    void delete_success() {
        when(taskMapper.deleteById(7L)).thenReturn(1);
        service.delete(7L);
        verify(taskMapper).deleteById(7L);
    }

    @Test
    @DisplayName("異常系: delete対象なしでNotFoundException")
    void delete_missing_throws() {
        when(taskMapper.deleteById(8L)).thenReturn(0);
        assertThatThrownBy(() -> service.delete(8L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("境界値: searchでpage<0とsize<=0はpage=0,size=20に補正")
    void search_normalizes_negative_page_and_zero_size() {
        when(taskMapper.search(isNull(), eq("x"), eq(0), eq(20))).thenReturn(List.of());
        when(taskMapper.count(isNull(), eq("x"))).thenReturn(0L);

        PageResult<Task> result = service.search(null, "x", -1, 0);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.total()).isEqualTo(0);

        verify(taskMapper).search(isNull(), eq("x"), eq(0), eq(20));
        verify(taskMapper).count(isNull(), eq("x"));
    }

    @Test
    @DisplayName("境界値: searchでsize上限は100、offsetはpage*size")
    void search_caps_size_to_100_and_computes_offset() {
        when(taskMapper.search(eq(TaskStatus.OPEN), eq(""), eq(200), eq(100))).thenReturn(List.of());
        when(taskMapper.count(eq(TaskStatus.OPEN), eq(""))).thenReturn(0L);

        PageResult<Task> result = service.search(TaskStatus.OPEN, "", 2, 1000);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(100);
        verify(taskMapper).search(eq(TaskStatus.OPEN), eq(""), eq(200), eq(100));
        verify(taskMapper).count(eq(TaskStatus.OPEN), eq(""));
    }
}

