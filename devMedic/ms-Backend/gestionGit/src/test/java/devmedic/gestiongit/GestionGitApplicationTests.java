package devmedic.gestiongit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Temporarily disabled - fix later")
class GestionGitApplicationTests {

    @Test
    void contextLoads() {
    }

}
