package devmedic.gestiongit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class GestionGitApplication {

    public static void main(String[] args) {
        SpringApplication.run(GestionGitApplication.class, args);
    }

}
