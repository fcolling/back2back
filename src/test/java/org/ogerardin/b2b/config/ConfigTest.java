package org.ogerardin.b2b.config;

import org.ogerardin.b2b.domain.FilesystemSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;

@EnableAutoConfiguration
@ComponentScan(basePackages = {"org.ogerardin.b2b.config"})
public class ConfigTest implements CommandLineRunner {


    @Autowired
    BackupSourceRepository repo;

    public static void main(String[] args) {
        new SpringApplicationBuilder(ConfigTest.class)
                .web(false)
                .run(args);
    }


    @Override
    public void run(String... args) throws Exception {

        repo.deleteAll();

        repo.save(new FilesystemSource("/tmp","/Users/Olivier/Documents"));

        repo.findAll().forEach(System.out::println);

        Thread.sleep(100000);

    }
}