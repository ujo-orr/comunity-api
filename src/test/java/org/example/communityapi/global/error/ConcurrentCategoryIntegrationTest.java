package org.example.communityapi.global.error;

import org.example.communityapi.category.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.batch.job.enabled=false"})
@AutoConfigureMockMvc
class ConcurrentCategoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @SpyBean CategoryRepository categories;

    @Test
    void twoRequestsPassingDuplicateCheckProduceOneSuccessAndOneConflict() throws Exception {
        String name = "race-test";
        var barrier = new CyclicBarrier(2);

        doAnswer(invocation -> {
            boolean exists = jdbc.queryForObject(
                    "select count(*) from categories where name = ?", Long.class, name) > 0;
            barrier.await(10, TimeUnit.SECONDS);
            return exists;
        }).when(categories).existsByName(name);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> create(name));
            var second = executor.submit(() -> create(name));
            var responses =
                    List.of(first.get(20, TimeUnit.SECONDS),
                            second.get(20, TimeUnit.SECONDS));
            assertThat(responses).extracting(r -> r.getStatus()).containsExactlyInAnyOrder(201, 409);

            var conflict =
                    responses
                            .stream()
                            .filter(r -> r.getStatus() == 409)
                            .findFirst().orElseThrow();
            assertThat(conflict.getContentAsString()).contains("C007").doesNotContain("insert into");

            assertThat(categories.findAll().stream().filter(c -> name.equals(c.getName())).count()).isEqualTo(1);
        } finally {
            categories.findAll().stream().filter(c -> name.equals(c.getName())).forEach(categories::delete);
        }
    }

    private org.springframework.mock.web.MockHttpServletResponse create(String name) throws Exception {
        return mvc.perform(post("/api/categories").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse();
    }
}
