package org.example.communityapi.schema;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.batch.job.enabled=false"})
class MemberSchemaIntegrationTest extends MemberSchemaContract {
}
